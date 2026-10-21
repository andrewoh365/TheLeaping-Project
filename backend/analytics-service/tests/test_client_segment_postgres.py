from datetime import date, datetime, timedelta, timezone
import os

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, text
from sqlalchemy.engine import make_url
from sqlalchemy.pool import NullPool

import app.repositories.analytics_repository as repository
from app.main import app
from app.schemas.dashboard import TimeGrouping
from app.schemas.filters import (
    AssetType,
    ClientSegment,
    DashboardFilters,
    OrderStatus,
)


UTC = timezone.utc
REPORTING_READER_PASSWORD = "reporting-reader-test-password"


@pytest.fixture()
def postgres_engine(monkeypatch):
    database_url = os.getenv("ANALYTICS_TEST_DATABASE_URL")

    if not database_url:
        pytest.skip(
            "ANALYTICS_TEST_DATABASE_URL is not configured"
        )

    test_engine = create_engine(
        database_url,
        poolclass=NullPool,
    )

    with test_engine.begin() as connection:
        connection.exec_driver_sql(
            """
            DROP TABLE IF EXISTS trades;
            DROP TABLE IF EXISTS trade_orders;
            DROP TABLE IF EXISTS instruments;
            DROP TABLE IF EXISTS markets;
            DROP TABLE IF EXISTS portfolios;
            """
        )
        connection.exec_driver_sql(
            """
            CREATE TABLE portfolios (
                portfolio_id BIGINT PRIMARY KEY,
                customer_id BIGINT NOT NULL UNIQUE
            );

            CREATE TABLE markets (
                market_id BIGINT PRIMARY KEY,
                market_name VARCHAR(100) NOT NULL
            );

            CREATE TABLE instruments (
                instrument_id BIGINT PRIMARY KEY,
                market_id BIGINT NOT NULL,
                symbol VARCHAR(50) NOT NULL,
                name VARCHAR(200) NOT NULL,
                instrument_type VARCHAR(20) NOT NULL,
                is_active BOOLEAN NOT NULL
            );

            CREATE TABLE trade_orders (
                order_id BIGINT PRIMARY KEY,
                portfolio_id BIGINT NOT NULL,
                instrument_id BIGINT NOT NULL,
                order_status VARCHAR(20) NOT NULL,
                rejection_reason VARCHAR(200),
                submitted_at TIMESTAMPTZ NOT NULL
            );

            CREATE TABLE trades (
                trade_id BIGINT PRIMARY KEY,
                order_id BIGINT NOT NULL,
                total_usd_value NUMERIC(30, 8) NOT NULL,
                executed_at TIMESTAMPTZ NOT NULL
            );
            """
        )
        connection.exec_driver_sql(
            """
            DO $$
            BEGIN
                IF EXISTS (
                    SELECT 1 FROM pg_roles WHERE rolname = 'leap_reporting'
                ) THEN
                    DROP OWNED BY leap_reporting;
                END IF;
            END
            $$;
            DROP ROLE IF EXISTS leap_reporting;
            CREATE ROLE leap_reporting LOGIN NOSUPERUSER NOCREATEDB
                NOCREATEROLE NOREPLICATION
                PASSWORD 'reporting-reader-test-password';
            ALTER ROLE leap_reporting SET default_transaction_read_only = on;
            GRANT CONNECT ON DATABASE analytics_test TO leap_reporting;
            GRANT USAGE ON SCHEMA public TO leap_reporting;
            GRANT SELECT ON ALL TABLES IN SCHEMA public TO leap_reporting;
            """
        )

    monkeypatch.setattr(repository, "engine", test_engine)

    try:
        yield test_engine
    finally:
        with test_engine.begin() as connection:
            connection.exec_driver_sql(
                """
                DROP TABLE IF EXISTS trades;
                DROP TABLE IF EXISTS trade_orders;
                DROP TABLE IF EXISTS instruments;
                DROP TABLE IF EXISTS markets;
                DROP TABLE IF EXISTS portfolios;
                """
            )
        test_engine.dispose()


def _timestamp(day: int, hour: int = 12) -> datetime:
    return datetime(
        2026,
        7 if day <= 31 else 9,
        day if day <= 31 else day - 31,
        hour,
        tzinfo=UTC,
    )


def _seed_boundary_data(test_engine):
    with test_engine.begin() as connection:
        connection.execute(
            text(
                """
                INSERT INTO portfolios (portfolio_id, customer_id)
                VALUES (:portfolio_id, :customer_id)
                """
            ),
            [
                {
                    "portfolio_id": portfolio_id,
                    "customer_id": 100 + portfolio_id,
                }
                for portfolio_id in range(1, 7)
            ],
        )
        connection.execute(
            text(
                """
                INSERT INTO markets (market_id, market_name)
                VALUES (1, 'NASDAQ'), (2, 'NYSE')
                """
            )
        )
        connection.execute(
            text(
                """
                INSERT INTO instruments (
                    instrument_id,
                    market_id,
                    symbol,
                    name,
                    instrument_type,
                    is_active
                )
                VALUES
                    (1, 1, 'DUPL', 'First Duplicate', 'STOCK', TRUE),
                    (2, 2, 'DUPL', 'Second Duplicate', 'CRYPTO', FALSE),
                    (3, 1, 'QUIET', 'No Recent Orders', 'STOCK', FALSE)
                """
            )
        )

        orders = []
        order_id = 1
        representative_orders = {}

        def add_order(
            portfolio_id,
            submitted_at,
            order_status,
            instrument_id,
        ):
            nonlocal order_id
            orders.append(
                {
                    "order_id": order_id,
                    "portfolio_id": portfolio_id,
                    "instrument_id": instrument_id,
                    "order_status": order_status,
                    "rejection_reason": (
                        "INSUFFICIENT_CASH"
                        if order_status == "REJECTED"
                        else None
                    ),
                    "submitted_at": submitted_at,
                }
            )
            representative_orders.setdefault(
                portfolio_id,
                order_id,
            )
            order_id += 1

        # Portfolio 1 has one order outside the window: zero in-window.
        add_order(1, _timestamp(2), "FILLED", 1)

        # Portfolio 2 has twenty outside orders and one order on the
        # inclusive start boundary: exactly one in-window order.
        for index in range(20):
            add_order(
                2,
                _timestamp(2) - timedelta(days=index + 1),
                "FILLED",
                1,
            )
        add_order(2, _timestamp(3), "FILLED", 1)

        # The remaining portfolios exercise 9, 10, 19, and 20 orders.
        for portfolio_id, count, status, instrument_id in [
            (3, 9, "ACCEPTED", 1),
            (4, 10, "REJECTED", 2),
            (5, 19, "FILLED", 1),
            (6, 20, "CANCELLED", 2),
        ]:
            for index in range(count):
                add_order(
                    portfolio_id,
                    _timestamp(3 + index),
                    status,
                    instrument_id,
                )

        connection.execute(
            text(
                """
                INSERT INTO trade_orders (
                    order_id,
                    portfolio_id,
                    instrument_id,
                    order_status,
                    rejection_reason,
                    submitted_at
                )
                VALUES (
                    :order_id,
                    :portfolio_id,
                    :instrument_id,
                    :order_status,
                    :rejection_reason,
                    :submitted_at
                )
                """
            ),
            orders,
        )

        trade_values = {
            2: 10,
            3: 20,
            4: 30,
            5: 40,
            6: 50,
        }
        trades = [
            {
                "trade_id": trade_id,
                "order_id": representative_orders[portfolio_id],
                "total_usd_value": value,
                "executed_at": datetime(
                    2026,
                    9,
                    30,
                    23,
                    59,
                    tzinfo=UTC,
                ),
            }
            for trade_id, (portfolio_id, value) in enumerate(
                trade_values.items(),
                start=1,
            )
        ]

        # This trade is linked to an in-window order but executes outside
        # the report range, so it must not contribute to volume.
        trades.append(
            {
                "trade_id": 6,
                "order_id": representative_orders[6] + 1,
                "total_usd_value": 999,
                "executed_at": datetime(
                    2026,
                    10,
                    1,
                    tzinfo=UTC,
                ),
            }
        )

        connection.execute(
            text(
                """
                INSERT INTO trades (
                    trade_id,
                    order_id,
                    total_usd_value,
                    executed_at
                )
                VALUES (
                    :trade_id,
                    :order_id,
                    :total_usd_value,
                    :executed_at
                )
                """
            ),
            trades,
        )


def _rows_by_segment(rows):
    return {row["segment"]: row for row in rows}


def test_segment_boundaries_window_and_report_filter_independence(
    postgres_engine,
):
    _seed_boundary_data(postgres_engine)

    with postgres_engine.connect() as connection:
        before_count = connection.execute(
            text("SELECT COUNT(*) FROM trade_orders")
        ).scalar_one()

    base_filters = DashboardFilters(
        date_from=date(2026, 7, 3),
        date_to=date(2026, 9, 30),
    )
    rows = _rows_by_segment(
        repository.get_client_segment_breakdown(base_filters)
    )

    assert set(rows) == {
        "INACTIVE",
        "OCCASIONAL",
        "REGULAR",
        "FREQUENT",
    }
    assert rows["INACTIVE"] == {
        "segment": "INACTIVE",
        "total_clients": 1,
        "active_clients": 0,
        "order_count": 0,
        "trading_volume_usd": 0,
    }
    assert rows["OCCASIONAL"]["total_clients"] == 2
    assert rows["OCCASIONAL"]["active_clients"] == 2
    assert rows["OCCASIONAL"]["order_count"] == 10
    assert rows["OCCASIONAL"]["trading_volume_usd"] == 30
    assert rows["REGULAR"]["total_clients"] == 2
    assert rows["REGULAR"]["active_clients"] == 2
    assert rows["REGULAR"]["order_count"] == 29
    assert rows["REGULAR"]["trading_volume_usd"] == 70
    assert rows["FREQUENT"]["total_clients"] == 1
    assert rows["FREQUENT"]["active_clients"] == 1
    assert rows["FREQUENT"]["order_count"] == 20
    assert rows["FREQUENT"]["trading_volume_usd"] == 50

    assert sum(row["order_count"] for row in rows.values()) == (
        repository.get_total_orders(base_filters)
    )
    assert sum(row["active_clients"] for row in rows.values()) == (
        repository.get_active_clients(base_filters)
    )
    assert sum(row["trading_volume_usd"] for row in rows.values()) == (
        repository.get_trading_volume(base_filters)
    )

    selected_rows = repository.get_client_segment_breakdown(
        DashboardFilters(
            date_from=date(2026, 7, 3),
            date_to=date(2026, 9, 30),
            client_segment=ClientSegment.REGULAR,
        )
    )
    assert [row["segment"] for row in selected_rows] == ["REGULAR"]

    # A later report start date changes report activity but not historical
    # segment populations, proving classification is anchored only to date_to.
    later_report = DashboardFilters(
        date_from=date(2026, 9, 1),
        date_to=date(2026, 9, 30),
    )
    later_rows = _rows_by_segment(
        repository.get_client_segment_breakdown(later_report)
    )
    assert [later_rows[name]["total_clients"] for name in rows] == [
        rows[name]["total_clients"] for name in rows
    ]
    assert sum(row["order_count"] for row in later_rows.values()) == 0

    # Instrument, market, and status filters affect report metrics, not the
    # unfiltered segment population.
    filtered = DashboardFilters(
        date_from=date(2026, 7, 3),
        date_to=date(2026, 9, 30),
        asset_type=AssetType.STOCK,
        market="NASDAQ",
        order_status=OrderStatus.FILLED,
    )
    filtered_rows = _rows_by_segment(
        repository.get_client_segment_breakdown(filtered)
    )
    assert [filtered_rows[name]["total_clients"] for name in rows] == [
        rows[name]["total_clients"] for name in rows
    ]

    with postgres_engine.connect() as connection:
        after_count = connection.execute(
            text("SELECT COUNT(*) FROM trade_orders")
        ).scalar_one()
    assert after_count == before_count


def test_instrument_filter_uses_id_and_preserves_historical_metadata(
    postgres_engine,
):
    _seed_boundary_data(postgres_engine)

    selected_filters = DashboardFilters(
        date_from=date(2026, 7, 3),
        date_to=date(2026, 9, 30),
        instrument_id=2,
    )

    metadata = repository.get_instrument_metadata(2)
    assert metadata == {
        "instrument_id": 2,
        "symbol": "DUPL",
        "name": "Second Duplicate",
        "instrument_type": "CRYPTO",
        "market_name": "NYSE",
        "is_active": False,
    }

    # Instrument 1 has the same symbol in another market. The ID predicate
    # must keep its orders and trades out of instrument 2's report.
    assert repository.get_total_orders(selected_filters) == 30
    assert repository.get_total_orders(
        DashboardFilters(
            date_from=date(2026, 7, 3),
            date_to=date(2026, 9, 30),
            instrument_id=1,
        )
    ) == 29
    assert repository.get_trading_volume(selected_filters) == 80
    assert repository.get_active_clients(selected_filters) == 2
    assert repository.get_fill_rate_counts(selected_filters) == {
        "total_orders": 30,
        "filled_orders": 0,
    }

    volume_rows = repository.get_trading_volume_rows(
        selected_filters,
        TimeGrouping.DAILY,
    )
    assert volume_rows == [
        {
            "period": "2026-09-30",
            "total_usd_value": 80,
        }
    ]

    outcome_rows = {
        row["order_status"]: row["order_count"]
        for row in repository.get_order_outcomes(selected_filters)
    }
    assert outcome_rows == {
        "REJECTED": 10,
        "CANCELLED": 20,
    }

    client_activity_rows = repository.get_client_activity_rows(
        selected_filters,
        TimeGrouping.DAILY,
    )
    assert client_activity_rows[0] == {
        "period": "2026-07-03",
        "active_clients": 2,
    }

    segment_rows = _rows_by_segment(
        repository.get_client_segment_breakdown(selected_filters)
    )
    assert segment_rows["REGULAR"] == {
        "segment": "REGULAR",
        "total_clients": 2,
        "active_clients": 1,
        "order_count": 10,
        "trading_volume_usd": 30,
    }
    assert segment_rows["FREQUENT"] == {
        "segment": "FREQUENT",
        "total_clients": 1,
        "active_clients": 1,
        "order_count": 20,
        "trading_volume_usd": 50,
    }

    # This inactive instrument has no orders but remains reportable by ID.
    quiet_filters = DashboardFilters(
        date_from=date(2026, 7, 3),
        date_to=date(2026, 9, 30),
        instrument_id=3,
    )
    assert repository.get_instrument_metadata(3) == {
        "instrument_id": 3,
        "symbol": "QUIET",
        "name": "No Recent Orders",
        "instrument_type": "STOCK",
        "market_name": "NASDAQ",
        "is_active": False,
    }
    assert repository.get_total_orders(quiet_filters) == 0
    assert repository.get_trading_volume(quiet_filters) == 0
    assert repository.get_active_clients(quiet_filters) == 0
    assert repository.get_fill_rate_counts(quiet_filters) == {
        "total_orders": 0,
        "filled_orders": 0,
    }


def test_instrument_outside_top_five_is_reportable_without_double_counting(
    postgres_engine,
):
    _seed_boundary_data(postgres_engine)

    with postgres_engine.begin() as connection:
        connection.execute(
            text(
                """
                INSERT INTO instruments (
                    instrument_id,
                    market_id,
                    symbol,
                    name,
                    instrument_type,
                    is_active
                )
                VALUES
                    (4, 1, 'AAA', 'Instrument AAA', 'STOCK', TRUE),
                    (5, 1, 'AAB', 'Instrument AAB', 'STOCK', TRUE),
                    (6, 1, 'AAC', 'Instrument AAC', 'STOCK', TRUE),
                    (7, 1, 'AAD', 'Instrument AAD', 'STOCK', TRUE),
                    (8, 1, 'ZZZ', 'Instrument ZZZ', 'STOCK', TRUE)
                """
            )
        )
        connection.execute(
            text(
                """
                INSERT INTO trade_orders (
                    order_id,
                    portfolio_id,
                    instrument_id,
                    order_status,
                    submitted_at
                )
                VALUES
                    (100, 1, 4, 'FILLED', '2026-09-15 12:00:00+00'),
                    (101, 1, 5, 'FILLED', '2026-09-15 12:00:00+00'),
                    (102, 1, 6, 'FILLED', '2026-09-15 12:00:00+00'),
                    (103, 1, 7, 'FILLED', '2026-09-15 12:00:00+00'),
                    (104, 1, 8, 'FILLED', '2026-09-15 12:00:00+00')
                """
            )
        )
        connection.execute(
            text(
                """
                INSERT INTO trades (
                    trade_id,
                    order_id,
                    total_usd_value,
                    executed_at
                )
                VALUES
                    (100, 104, 10, '2026-09-15 12:00:00+00'),
                    (101, 104, 20, '2026-09-15 13:00:00+00')
                """
            )
        )

    base_filters = DashboardFilters(
        date_from=date(2026, 7, 3),
        date_to=date(2026, 9, 30),
    )
    top_five_symbols = {
        row["symbol"]
        for row in repository.get_most_active_instruments(
            base_filters,
            limit=5,
        )
    }
    assert "ZZZ" not in top_five_symbols

    selected_filters = DashboardFilters(
        date_from=date(2026, 7, 3),
        date_to=date(2026, 9, 30),
        instrument_id=8,
    )
    assert repository.get_total_orders(selected_filters) == 1
    assert repository.get_trading_volume(selected_filters) == 30
    assert repository.get_active_clients(selected_filters) == 1
    assert repository.get_fill_rate_counts(selected_filters) == {
        "total_orders": 1,
        "filled_orders": 1,
    }
    assert repository.get_most_active_instruments(
        selected_filters,
        limit=5,
    ) == [
        {
            "symbol": "ZZZ",
            "order_count": 1,
        }
    ]


@pytest.mark.parametrize(
    "order_status, expected_orders, expected_volume, expected_clients, "
    "expected_filled_orders, expected_rejection_count",
    [
        (None, 59, 150, 5, 20, 10),
        (OrderStatus.FILLED, 20, 50, 2, 20, 0),
        (OrderStatus.REJECTED, 10, 30, 1, 0, 10),
        (OrderStatus.CANCELLED, 20, 50, 1, 0, 0),
        (OrderStatus.ACCEPTED, 9, 20, 1, 0, 0),
        (OrderStatus.SUBMITTED, 0, 0, 0, 0, 0),
    ],
)
def test_order_status_filters_all_reporting_activity(
    postgres_engine,
    order_status,
    expected_orders,
    expected_volume,
    expected_clients,
    expected_filled_orders,
    expected_rejection_count,
):
    _seed_boundary_data(postgres_engine)

    filters = DashboardFilters(
        date_from=date(2026, 7, 3),
        date_to=date(2026, 9, 30),
        order_status=order_status,
    )

    assert repository.get_total_orders(filters) == expected_orders
    assert repository.get_active_clients(filters) == expected_clients

    client_activity_rows = repository.get_client_activity_rows(
        filters,
        TimeGrouping.MONTHLY,
    )
    if expected_clients:
        assert client_activity_rows == [
            {
                "period": "2026-07-01",
                "active_clients": expected_clients,
            }
        ]
    else:
        assert client_activity_rows == []

    assert repository.get_fill_rate_counts(filters) == {
        "total_orders": expected_orders,
        "filled_orders": expected_filled_orders,
    }

    outcome_rows = repository.get_order_outcomes(filters)
    assert sum(row["order_count"] for row in outcome_rows) == (
        expected_orders
    )
    if order_status is None:
        assert {
            row["order_status"]: row["order_count"]
            for row in outcome_rows
        } == {
            "FILLED": 20,
            "REJECTED": 10,
            "CANCELLED": 20,
            "ACCEPTED": 9,
        }
    elif expected_orders:
        assert outcome_rows == [
            {
                "order_status": order_status.value,
                "order_count": expected_orders,
            }
        ]
    else:
        assert outcome_rows == []

    volume_rows = repository.get_trading_volume_rows(
        filters,
        TimeGrouping.DAILY,
    )
    assert sum(row["total_usd_value"] for row in volume_rows) == (
        expected_volume
    )
    assert repository.get_trading_volume(filters) == expected_volume

    top_instruments = repository.get_most_active_instruments(
        filters,
        limit=5,
    )
    assert sum(row["order_count"] for row in top_instruments) == (
        expected_orders
    )

    rejection_rows = repository.get_rejection_reasons(filters, limit=5)
    if expected_rejection_count:
        assert rejection_rows == [
            {
                "rejection_reason": "INSUFFICIENT_CASH",
                "rejection_count": expected_rejection_count,
            }
        ]
    else:
        assert rejection_rows == []

    segment_rows = repository.get_client_segment_breakdown(filters)
    assert sum(row["order_count"] for row in segment_rows) == (
        expected_orders
    )
    assert sum(row["trading_volume_usd"] for row in segment_rows) == (
        expected_volume
    )


def test_order_status_combines_with_instrument_and_client_segment(
    postgres_engine,
):
    _seed_boundary_data(postgres_engine)

    instrument_status = DashboardFilters(
        date_from=date(2026, 7, 3),
        date_to=date(2026, 9, 30),
        instrument_id=2,
        order_status=OrderStatus.REJECTED,
    )
    assert repository.get_total_orders(instrument_status) == 10
    assert repository.get_trading_volume(instrument_status) == 30
    assert repository.get_rejection_reasons(
        instrument_status,
        limit=5,
    ) == [
        {
            "rejection_reason": "INSUFFICIENT_CASH",
            "rejection_count": 10,
        }
    ]

    segment_status = DashboardFilters(
        date_from=date(2026, 7, 3),
        date_to=date(2026, 9, 30),
        client_segment=ClientSegment.REGULAR,
        order_status=OrderStatus.REJECTED,
    )
    segment_rows = repository.get_client_segment_breakdown(segment_status)
    assert segment_rows == [
        {
            "segment": "REGULAR",
            "total_clients": 2,
            "active_clients": 1,
            "order_count": 10,
            "trading_volume_usd": 30,
        }
    ]

    all_filters = DashboardFilters(
        date_from=date(2026, 7, 3),
        date_to=date(2026, 9, 30),
        instrument_id=2,
        client_segment=ClientSegment.FREQUENT,
        order_status=OrderStatus.CANCELLED,
    )
    assert repository.get_total_orders(all_filters) == 20
    assert repository.get_trading_volume(all_filters) == 50
    assert repository.get_client_segment_breakdown(all_filters) == [
        {
            "segment": "FREQUENT",
            "total_clients": 1,
            "active_clients": 1,
            "order_count": 20,
            "trading_volume_usd": 50,
        }
    ]

    # There are no rejected orders for instrument 1, so status filtering
    # must exclude all activity and volume rather than leaking instrument 2.
    no_matching_status = DashboardFilters(
        date_from=date(2026, 7, 3),
        date_to=date(2026, 9, 30),
        instrument_id=1,
        order_status=OrderStatus.REJECTED,
    )
    assert repository.get_total_orders(no_matching_status) == 0
    assert repository.get_trading_volume(no_matching_status) == 0


def test_utc_boundaries_grouping_and_non_utc_session_timezone(
    postgres_engine,
):
    with postgres_engine.begin() as connection:
        connection.execute(
            text(
                """
                INSERT INTO portfolios (portfolio_id, customer_id)
                VALUES (10, 1010)
                """
            )
        )
        connection.execute(
            text(
                """
                INSERT INTO markets (market_id, market_name)
                VALUES (10, 'UTC-TEST')
                """
            )
        )
        connection.execute(
            text(
                """
                INSERT INTO instruments (
                    instrument_id,
                    market_id,
                    symbol,
                    name,
                    instrument_type,
                    is_active
                )
                VALUES
                    (10, 10, 'UTC', 'UTC Boundary Instrument',
                     'STOCK', TRUE)
                """
            )
        )
        connection.execute(
            text(
                """
                INSERT INTO trade_orders (
                    order_id,
                    portfolio_id,
                    instrument_id,
                    order_status,
                    submitted_at
                )
                VALUES
                    (100, 10, 10, 'FILLED',
                     '2028-02-28 23:59:59.999999+00'),
                    (101, 10, 10, 'FILLED',
                     '2028-02-29 00:00:00+00'),
                    (102, 10, 10, 'FILLED',
                     '2028-02-29 23:59:59.999999+00'),
                    (103, 10, 10, 'FILLED',
                     '2028-03-01 00:00:00+00'),
                    (104, 10, 10, 'FILLED',
                     '2025-12-31 23:59:59.999999+00'),
                    (105, 10, 10, 'FILLED',
                     '2026-01-01 00:00:00+00'),
                    (106, 10, 10, 'FILLED',
                     '2026-03-01 00:00:00+00'),
                    (107, 10, 10, 'FILLED',
                     '2026-03-02 00:00:00+00'),
                    (108, 10, 10, 'FILLED',
                     '2028-02-29 12:00:00+00')
                """
            )
        )
        connection.execute(
            text(
                """
                INSERT INTO trades (
                    trade_id,
                    order_id,
                    total_usd_value,
                    executed_at
                )
                VALUES
                    (100, 101, 10, '2028-02-29 00:00:00+00'),
                    (101, 102, 20, '2028-02-29 23:59:59.999999+00'),
                    (102, 103, 30, '2028-03-01 00:00:00+00'),
                    (103, 104, 40, '2025-12-31 23:59:59.999999+00'),
                    (104, 105, 50, '2026-01-01 00:00:00+00'),
                    (105, 106, 60, '2026-03-01 00:00:00+00'),
                    (106, 107, 70, '2026-03-02 00:00:00+00'),
                    (107, 108, 80, '2028-03-01 00:00:00+00')
                """
            )
        )

        role = connection.execute(
            text("SELECT CURRENT_USER")
        ).scalar_one()
        quoted_role = '"' + role.replace('"', '""') + '"'
        connection.exec_driver_sql(
            f"ALTER ROLE {quoted_role} SET TIME ZONE "
            "'America/Los_Angeles'"
        )

    try:
        leap_day = DashboardFilters(
            date_from=date(2028, 2, 29),
            date_to=date(2028, 2, 29),
            instrument_id=10,
            order_status=OrderStatus.FILLED,
        )

        # Start and fractional-second end events are included. The event at
        # the exclusive next-midnight boundary is excluded.
        assert repository.get_total_orders(leap_day) == 3
        assert repository.get_trading_volume(leap_day) == 30
        assert repository.get_trading_volume_rows(
            leap_day,
            TimeGrouping.DAILY,
        ) == [
            {
                "period": "2028-02-29",
                "total_usd_value": 30,
            }
        ]

        month_boundary = DashboardFilters(
            date_from=date(2025, 12, 31),
            date_to=date(2026, 1, 1),
            instrument_id=10,
            order_status=OrderStatus.FILLED,
        )
        assert repository.get_trading_volume_rows(
            month_boundary,
            TimeGrouping.MONTHLY,
        ) == [
            {
                "period": "2025-12-01",
                "total_usd_value": 40,
            },
            {
                "period": "2026-01-01",
                "total_usd_value": 50,
            },
        ]

        week_boundary = DashboardFilters(
            date_from=date(2026, 3, 1),
            date_to=date(2026, 3, 2),
            instrument_id=10,
            order_status=OrderStatus.FILLED,
        )
        assert repository.get_trading_volume_rows(
            week_boundary,
            TimeGrouping.WEEKLY,
        ) == [
            {
                "period": "2026-02-23",
                "total_usd_value": 60,
            },
            {
                "period": "2026-03-02",
                "total_usd_value": 70,
            }
        ]

        # Order 108 is submitted inside the leap-day range but executes on
        # March 1, so it contributes to orders but not executed volume.
        assert repository.get_total_orders(leap_day) == 3
        assert repository.get_trading_volume(leap_day) == 30
    finally:
        with postgres_engine.begin() as connection:
            role = connection.execute(
                text("SELECT CURRENT_USER")
            ).scalar_one()
            quoted_role = '"' + role.replace('"', '""') + '"'
            connection.exec_driver_sql(
                f"ALTER ROLE {quoted_role} RESET TIME ZONE"
            )


def test_dashboard_http_endpoint_uses_real_postgresql_fixture(
    postgres_engine,
):
    _seed_boundary_data(postgres_engine)

    response = TestClient(app).get(
        "/analytics/dashboard",
        headers={
            "X-Analytics-Internal-Token": "test-analytics-internal-token"
        },
        params={
            "date_from": "2026-07-03",
            "date_to": "2026-09-30",
            "instrument_id": "2",
            "order_status": "REJECTED",
            "client_segment": "REGULAR",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["summary"]["total_orders"] == 10
    assert body["summary"]["trading_volume_usd"] == 30.0
    assert body["summary"]["active_clients"] == 1
    assert body["summary"]["filled_orders_percentage"] == 0.0
    assert body["selected_instrument"] == {
        "instrument_id": 2,
        "symbol": "DUPL",
        "name": "Second Duplicate",
        "instrument_type": "CRYPTO",
        "market_name": "NYSE",
        "is_active": False,
    }
    assert body["reporting_period"] == {
        "date_from": "2026-07-03",
        "date_to": "2026-09-30",
        "timezone": "UTC",
    }
    assert body["client_segment_breakdown"] == [
        {
            "segment": "REGULAR",
            "total_clients": 2,
            "active_clients": 1,
            "order_count": 10,
            "trading_volume_usd": 30.0,
        }
    ]


def test_reporting_reader_role_is_read_only(postgres_engine):
    with postgres_engine.begin() as connection:
        connection.execute(
            text(
                """
                INSERT INTO markets (market_id, market_name)
                VALUES (20, 'READER-TEST')
                """
            )
        )

    database_url = os.environ["ANALYTICS_TEST_DATABASE_URL"]
    reader_url = make_url(database_url).set(
        username="leap_reporting",
        password=REPORTING_READER_PASSWORD,
    )
    reader_engine = create_engine(reader_url, poolclass=NullPool)

    try:
        with reader_engine.connect() as connection:
            assert connection.execute(
                text("SELECT COUNT(*) FROM markets")
            ).scalar_one() == 1

            with pytest.raises(Exception, match="read-only"):
                connection.execute(
                    text(
                        "INSERT INTO markets (market_id, market_name) "
                        "VALUES (21, 'SHOULD-FAIL')"
                    )
                )
    finally:
        reader_engine.dispose()

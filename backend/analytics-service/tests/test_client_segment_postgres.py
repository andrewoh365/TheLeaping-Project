from datetime import date, datetime, timedelta, timezone
import os

import pytest
from sqlalchemy import create_engine, text
from sqlalchemy.pool import NullPool

import app.repositories.analytics_repository as repository
from app.schemas.dashboard import TimeGrouping
from app.schemas.filters import (
    AssetType,
    ClientSegment,
    DashboardFilters,
    OrderStatus,
)


UTC = timezone.utc


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
                    submitted_at
                )
                VALUES (
                    :order_id,
                    :portfolio_id,
                    :instrument_id,
                    :order_status,
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

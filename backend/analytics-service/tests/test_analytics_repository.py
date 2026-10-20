from datetime import date

import app.repositories.analytics_repository as repository
from app.schemas.dashboard import TimeGrouping
from app.schemas.filters import (
    AssetType,
    ClientSegment,
    DashboardFilters,
    OrderStatus,
)


class _Result:
    def __init__(self, rows):
        self.rows = rows

    def mappings(self):
        return self

    def all(self):
        return self.rows

    def one_or_none(self):
        if len(self.rows) > 1:
            raise AssertionError("Expected at most one row")

        return self.rows[0] if self.rows else None


class _Connection:
    def __init__(self, rows):
        self.rows = rows
        self.query = None
        self.params = None

    def __enter__(self):
        return self

    def __exit__(self, exception_type, exception, traceback):
        return False

    def execute(self, query, params):
        self.query = str(query)
        self.params = params
        return _Result(self.rows)


class _Engine:
    def __init__(self, rows):
        self.connection = _Connection(rows)

    def connect(self):
        return self.connection


def _filters():
    return DashboardFilters(
        date_from=date(2026, 9, 1),
        date_to=date(2026, 9, 30),
    )


def test_instrument_metadata_lookup_includes_inactive_instruments(
    monkeypatch,
):
    engine = _Engine(
        [
            {
                "instrument_id": 15,
                "symbol": "AAPL",
                "name": "Apple Inc.",
                "instrument_type": "STOCK",
                "market_name": "NASDAQ",
                "is_active": False,
            }
        ]
    )
    monkeypatch.setattr(repository, "engine", engine)

    metadata = repository.get_instrument_metadata(15)

    assert metadata == engine.connection.rows[0]
    assert "FROM instruments i" in engine.connection.query
    assert "JOIN markets m" in engine.connection.query
    assert "WHERE i.instrument_id = :instrument_id" in (
        engine.connection.query
    )
    assert "trade_orders" not in engine.connection.query
    assert "trades" not in engine.connection.query
    assert engine.connection.params == {"instrument_id": 15}


def test_instrument_metadata_lookup_returns_none_for_unknown_id(
    monkeypatch,
):
    engine = _Engine([])
    monkeypatch.setattr(repository, "engine", engine)

    assert repository.get_instrument_metadata(999) is None


def test_instrument_filter_is_parameterized_and_uses_id(monkeypatch):
    engine = _Engine([])
    monkeypatch.setattr(repository, "engine", engine)

    repository.get_order_outcomes(
        DashboardFilters(instrument_id=15),
    )

    assert "o.instrument_id = :instrument_id" in engine.connection.query
    assert "i.symbol = :instrument_id" not in engine.connection.query
    assert engine.connection.params["instrument_id"] == 15


def test_multiple_filters_are_combined(monkeypatch):
    engine = _Engine([])
    monkeypatch.setattr(repository, "engine", engine)

    filters = DashboardFilters(
        date_from=date(2026, 9, 1),
        date_to=date(2026, 9, 30),
        asset_type=AssetType.STOCK,
        market="NASDAQ",
        instrument_id=15,
        order_status=OrderStatus.FILLED,
        client_segment=ClientSegment.REGULAR,
    )

    repository.get_most_active_instruments(filters, limit=5)

    query = engine.connection.query
    params = engine.connection.params

    assert "o.instrument_id = :instrument_id" in query
    assert "i.instrument_type = :asset_type" in query
    assert "m.market_name = :market" in query
    assert "o.order_status = :order_status" in query
    assert "o.portfolio_id IN (" in query
    assert params["instrument_id"] == 15
    assert params["asset_type"] == "STOCK"
    assert params["market"] == "NASDAQ"
    assert params["order_status"] == "FILLED"
    assert params["client_segment"] == "REGULAR"


def test_missing_instrument_id_preserves_existing_filters(monkeypatch):
    engine = _Engine([])
    monkeypatch.setattr(repository, "engine", engine)

    repository.get_order_outcomes(_filters())

    assert "o.instrument_id = :instrument_id" not in engine.connection.query
    assert "instrument_id" not in engine.connection.params


def test_trading_volume_is_grouped_in_database(monkeypatch):
    engine = _Engine([])
    monkeypatch.setattr(repository, "engine", engine)

    repository.get_trading_volume_rows(
        _filters(),
        TimeGrouping.WEEKLY,
    )

    assert "DATE_TRUNC('week', t.executed_at)" in engine.connection.query
    assert "SUM(t.total_usd_value)" in engine.connection.query
    assert "GROUP BY 1" in engine.connection.query


def test_client_activity_is_grouped_and_distinct_in_database(monkeypatch):
    engine = _Engine([])
    monkeypatch.setattr(repository, "engine", engine)

    repository.get_client_activity_rows(
        _filters(),
        TimeGrouping.MONTHLY,
    )

    assert "DATE_TRUNC('month', o.submitted_at)" in engine.connection.query
    assert "COUNT(DISTINCT p.customer_id)" in engine.connection.query
    assert "GROUP BY 1" in engine.connection.query


def test_ranked_analytics_lists_are_limited_in_database(monkeypatch):
    engine = _Engine([])
    monkeypatch.setattr(repository, "engine", engine)

    repository.get_most_active_instruments(_filters(), limit=5)

    assert "LIMIT :result_limit" in engine.connection.query
    assert engine.connection.params["result_limit"] == 5

    repository.get_rejection_reasons(_filters(), limit=5)

    assert "LIMIT :result_limit" in engine.connection.query
    assert engine.connection.params["result_limit"] == 5


def test_client_segment_breakdown_uses_unfiltered_segments_and_report_filters(
    monkeypatch,
):
    engine = _Engine([])
    monkeypatch.setattr(repository, "engine", engine)

    filters = DashboardFilters(
        date_from=date(2026, 9, 1),
        date_to=date(2026, 9, 30),
        instrument_id=15,
        client_segment=ClientSegment.REGULAR,
    )

    repository.get_client_segment_breakdown(filters)

    query = engine.connection.query
    params = engine.connection.params

    assert "LEFT JOIN trade_orders segment_orders" in query
    assert "COUNT(DISTINCT p.customer_id) AS active_clients" in query
    assert "COUNT(o.order_id) AS order_count" in query
    assert "SUM(t.total_usd_value)" in query
    assert "o.submitted_at >= :date_from" in query
    assert "o.submitted_at < (:date_to + INTERVAL '1 day')" in query
    assert "t.executed_at >= :date_from" in query
    assert "t.executed_at < (:date_to + INTERVAL '1 day')" in query
    assert "('INACTIVE')" in query
    assert "('FREQUENT')" in query
    assert "COALESCE(segment_populations.total_clients, 0)" in query
    assert "COALESCE(order_metrics.order_count, 0)" in query
    assert "COALESCE(volume_metrics.trading_volume_usd, 0)" in query
    assert "o.instrument_id = :instrument_id" in query
    assert "segment_orders.instrument_id" not in query
    assert "client_segments.client_segment = :client_segment" not in query
    assert "segment_values.segment = :client_segment" in query

    classification_sql = query.split(
        "WITH client_segments AS",
        1,
    )[1].split("segment_values(segment)", 1)[0]
    population_sql = query.split(
        "segment_populations AS",
        1,
    )[1].split("order_metrics AS", 1)[0]

    assert "instrument_id" not in classification_sql
    assert "instrument_id" not in population_sql

    assert params["segment_start"] == date(2026, 7, 3)
    assert params["segment_end"] == date(2026, 9, 30)
    assert params["instrument_id"] == 15
    assert params["client_segment"] == "REGULAR"


def test_client_segment_breakdown_returns_all_segments_without_selection(
    monkeypatch,
):
    engine = _Engine(
        [
            {
                "segment": "INACTIVE",
                "total_clients": 2,
                "active_clients": 0,
                "order_count": 0,
                "trading_volume_usd": 0,
            },
            {
                "segment": "OCCASIONAL",
                "total_clients": 0,
                "active_clients": 0,
                "order_count": 0,
                "trading_volume_usd": 0,
            },
            {
                "segment": "REGULAR",
                "total_clients": 0,
                "active_clients": 0,
                "order_count": 0,
                "trading_volume_usd": 0,
            },
            {
                "segment": "FREQUENT",
                "total_clients": 1,
                "active_clients": 1,
                "order_count": 22,
                "trading_volume_usd": 2000,
            },
        ]
    )
    monkeypatch.setattr(repository, "engine", engine)

    rows = repository.get_client_segment_breakdown(_filters())

    assert [row["segment"] for row in rows] == [
        "INACTIVE",
        "OCCASIONAL",
        "REGULAR",
        "FREQUENT",
    ]
    assert rows[1]["total_clients"] == 0
    assert rows[1]["active_clients"] == 0
    assert rows[1]["order_count"] == 0
    assert rows[1]["trading_volume_usd"] == 0
    assert "segment_values.segment = :client_segment" not in (
        engine.connection.query
    )
    assert "client_segment" not in engine.connection.params
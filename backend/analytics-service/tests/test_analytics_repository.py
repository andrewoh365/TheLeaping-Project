from datetime import date

import app.repositories.analytics_repository as repository
from app.schemas.dashboard import TimeGrouping
from app.schemas.filters import DashboardFilters


class _Result:
    def __init__(self, rows):
        self.rows = rows

    def mappings(self):
        return self

    def all(self):
        return self.rows


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
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal

import pytest

import app.services.dashboard_service as service
from app.schemas.dashboard import TimeGrouping
from app.schemas.filters import DashboardFilters


def test_default_date_range_is_90_days():
    filters = DashboardFilters()

    effective_filters = service._apply_default_date_range(filters)

    number_of_days = (
        effective_filters.date_to - effective_filters.date_from
    ).days + 1

    assert number_of_days == 90


@pytest.mark.parametrize(
    "number_of_days, expected_grouping",
    [
        (31, TimeGrouping.DAILY),
        (32, TimeGrouping.WEEKLY),
        (180, TimeGrouping.WEEKLY),
        (181, TimeGrouping.MONTHLY),
    ],
)
def test_time_grouping_rules(
    number_of_days,
    expected_grouping,
):
    date_from = date(2026, 1, 1)
    date_to = date_from + timedelta(
        days=number_of_days - 1
    )

    filters = DashboardFilters(
        date_from=date_from,
        date_to=date_to,
    )

    grouping = service._determine_time_grouping(
        filters
    )

    assert grouping == expected_grouping


def test_build_dashboard(monkeypatch):
    monkeypatch.setattr(
        service,
        "get_total_orders",
        lambda filters: 100,
    )

    monkeypatch.setattr(
        service,
        "get_trading_volume",
        lambda filters: Decimal("250000.75"),
    )

    monkeypatch.setattr(
        service,
        "get_active_clients",
        lambda filters: 20,
    )

    monkeypatch.setattr(
        service,
        "get_fill_rate_counts",
        lambda filters: {
            "total_orders": 100,
            "filled_orders": 75,
        },
    )

    monkeypatch.setattr(
        service,
        "get_trading_volume_rows",
        lambda filters: [
            {
                "executed_at": datetime(
                    2026,
                    9,
                    1,
                    tzinfo=timezone.utc,
                ),
                "total_usd_value": Decimal("1000.00"),
            },
            {
                "executed_at": datetime(
                    2026,
                    9,
                    2,
                    tzinfo=timezone.utc,
                ),
                "total_usd_value": Decimal("1500.00"),
            },
        ],
    )

    monkeypatch.setattr(
        service,
        "get_order_outcomes",
        lambda filters: [
            {
                "order_status": "FILLED",
                "order_count": 75,
            },
            {
                "order_status": "REJECTED",
                "order_count": 15,
            },
            {
                "order_status": "CANCELLED",
                "order_count": 5,
            },
            {
                "order_status": "ACCEPTED",
                "order_count": 3,
            },
            {
                "order_status": "SUBMITTED",
                "order_count": 2,
            },
        ],
    )

    monkeypatch.setattr(
        service,
        "get_most_active_instruments",
        lambda filters: [
            {"symbol": "AAPL", "order_count": 30},
            {"symbol": "MSFT", "order_count": 25},
            {"symbol": "BTC-USD", "order_count": 20},
            {"symbol": "NVDA", "order_count": 15},
            {"symbol": "ETH-USD", "order_count": 10},
            {"symbol": "GBP/USD", "order_count": 5},
        ],
    )

    monkeypatch.setattr(
        service,
        "get_client_activity_rows",
        lambda filters: [
            {
                "submitted_at": datetime(
                    2026,
                    9,
                    1,
                    tzinfo=timezone.utc,
                ),
                "customer_id": 1,
            },
            {
                "submitted_at": datetime(
                    2026,
                    9,
                    1,
                    tzinfo=timezone.utc,
                ),
                "customer_id": 2,
            },
            {
                "submitted_at": datetime(
                    2026,
                    9,
                    2,
                    tzinfo=timezone.utc,
                ),
                "customer_id": 1,
            },
        ],
    )

    monkeypatch.setattr(
        service,
        "get_rejection_reasons",
        lambda filters: [
            {
                "rejection_reason": "INSUFFICIENT_CASH",
                "rejection_count": 10,
            },
            {
                "rejection_reason": "MARKET_CLOSED",
                "rejection_count": 8,
            },
            {
                "rejection_reason": "PRICE_UNAVAILABLE",
                "rejection_count": 6,
            },
            {
                "rejection_reason": "INSUFFICIENT_HOLDINGS",
                "rejection_count": 5,
            },
            {
                "rejection_reason": "INSTRUMENT_NOT_TRADABLE",
                "rejection_count": 4,
            },
            {
                "rejection_reason": "OTHER",
                "rejection_count": 1,
            },
        ],
    )

    filters = DashboardFilters(
        date_from=date(2026, 9, 1),
        date_to=date(2026, 9, 30),
    )

    dashboard = service.build_dashboard(filters)

    assert dashboard.summary.total_orders == 100
    assert dashboard.summary.trading_volume_usd == 250000.75
    assert dashboard.summary.active_clients == 20
    assert dashboard.summary.filled_orders_percentage == 75.0

    assert (
        dashboard.trading_volume_over_time.grouping
        == TimeGrouping.DAILY
    )

    assert len(
        dashboard.trading_volume_over_time.points
    ) == 2

    assert len(dashboard.order_outcomes) == 5

    filled_outcome = next(
        outcome
        for outcome in dashboard.order_outcomes
        if outcome.status.value == "FILLED"
    )

    assert filled_outcome.count == 75
    assert filled_outcome.percentage == 75.0

    assert len(
        dashboard.most_active_instruments
    ) == 5

    assert (
        dashboard.most_active_instruments[0].symbol
        == "AAPL"
    )

    assert (
        dashboard.client_activity_trend.grouping
        == TimeGrouping.DAILY
    )

    assert len(
        dashboard.client_activity_trend.points
    ) == 2

    assert (
        dashboard.client_activity_trend.points[0]
        .active_clients
        == 2
    )

    assert len(
        dashboard.top_rejection_reasons
    ) == 5
from datetime import date, datetime, timedelta
from decimal import Decimal

import pytest

import app.services.dashboard_service as service
from app.schemas.dashboard import TimeGrouping
from app.schemas.filters import ClientSegment, DashboardFilters


def test_default_date_range_is_90_days():
    filters = DashboardFilters()

    effective_filters = service._apply_default_date_range(filters)

    number_of_days = (
        effective_filters.date_to - effective_filters.date_from
    ).days + 1

    assert number_of_days == 90


def test_effective_date_defaults_use_utc_today(monkeypatch):
    class FixedDateTime:
        @classmethod
        def now(cls, requested_timezone):
            return datetime(
                2026,
                10,
                20,
                0,
                30,
                tzinfo=requested_timezone,
            )

    monkeypatch.setattr(service, "datetime", FixedDateTime)

    both_omitted = service._apply_default_date_range(
        DashboardFilters()
    )
    assert both_omitted.date_from == date(2026, 7, 23)
    assert both_omitted.date_to == date(2026, 10, 20)

    only_end = service._apply_default_date_range(
        DashboardFilters(date_to=date(2026, 10, 10))
    )
    assert only_end.date_from == date(2026, 7, 13)
    assert only_end.date_to == date(2026, 10, 10)

    only_start = service._apply_default_date_range(
        DashboardFilters(date_from=date(2026, 10, 1))
    )
    assert only_start.date_from == date(2026, 10, 1)
    assert only_start.date_to == date(2026, 10, 20)

    explicit = service._apply_default_date_range(
        DashboardFilters(
            date_from=date(2028, 2, 28),
            date_to=date(2028, 2, 29),
        )
    )
    assert explicit.date_from == date(2028, 2, 28)
    assert explicit.date_to == date(2028, 2, 29)


def test_effective_reversed_range_is_rejected():
    with pytest.raises(service.InvalidDateRangeError):
        service._apply_default_date_range(
            DashboardFilters(date_from=date(2099, 10, 21))
        )


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
        lambda filters, grouping: [
            {
                "period": "2026-09-01",
                "total_usd_value": Decimal("1000.00"),
            },
            {
                "period": "2026-09-02",
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
        lambda filters, limit: [
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
        lambda filters, grouping: [
            {
                "period": "2026-09-01",
                "active_clients": 2,
            },
            {
                "period": "2026-09-02",
                "active_clients": 1,
            },
        ],
    )

    monkeypatch.setattr(
        service,
        "get_rejection_reasons",
        lambda filters, limit: [
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

    monkeypatch.setattr(
        service,
        "get_client_segment_breakdown",
        lambda filters: [
            {
                "segment": "INACTIVE",
                "total_clients": 4,
                "active_clients": 0,
                "order_count": 0,
                "trading_volume_usd": Decimal("0"),
            },
            {
                "segment": "OCCASIONAL",
                "total_clients": 3,
                "active_clients": 2,
                "order_count": 5,
                "trading_volume_usd": Decimal("100.256"),
            },
            {
                "segment": "REGULAR",
                "total_clients": 2,
                "active_clients": 1,
                "order_count": 12,
                "trading_volume_usd": Decimal("500.50"),
            },
            {
                "segment": "FREQUENT",
                "total_clients": 1,
                "active_clients": 1,
                "order_count": 25,
                "trading_volume_usd": Decimal("1000.75"),
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

    assert len(dashboard.order_outcomes) == 6

    filled_outcome = next(
        outcome
        for outcome in dashboard.order_outcomes
        if outcome.status.value == "FILLED"
    )

    assert filled_outcome.count == 75
    assert filled_outcome.percentage == 75.0

    expired_outcome = next(
        outcome
        for outcome in dashboard.order_outcomes
        if outcome.status.value == "EXPIRED"
    )

    assert expired_outcome.count == 0
    assert expired_outcome.percentage == 0.0

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

    assert len(dashboard.client_segment_breakdown) == 4
    occasional = dashboard.client_segment_breakdown[1]
    assert occasional.segment == ClientSegment.OCCASIONAL
    assert occasional.total_clients == 3
    assert occasional.active_clients == 2
    assert occasional.order_count == 5
    assert occasional.trading_volume_usd == 100.26
    assert dashboard.reporting_period.model_dump() == {
        "date_from": date(2026, 9, 1),
        "date_to": date(2026, 9, 30),
        "timezone": "UTC",
    }


def test_selected_instrument_with_no_activity_returns_zero_metrics(
    monkeypatch,
):
    metadata_calls = []

    def fake_get_instrument_metadata(instrument_id):
        metadata_calls.append(instrument_id)
        return {
            "instrument_id": instrument_id,
            "symbol": "DELISTED",
            "name": "Historical Instrument",
            "instrument_type": "STOCK",
            "market_name": "NYSE",
            "is_active": False,
        }

    monkeypatch.setattr(
        service,
        "get_instrument_metadata",
        fake_get_instrument_metadata,
    )
    monkeypatch.setattr(service, "get_total_orders", lambda filters: 0)
    monkeypatch.setattr(
        service,
        "get_trading_volume",
        lambda filters: Decimal("0"),
    )
    monkeypatch.setattr(service, "get_active_clients", lambda filters: 0)
    monkeypatch.setattr(
        service,
        "get_fill_rate_counts",
        lambda filters: {
            "total_orders": 0,
            "filled_orders": 0,
        },
    )
    monkeypatch.setattr(
        service,
        "get_trading_volume_rows",
        lambda filters, grouping: [],
    )
    monkeypatch.setattr(service, "get_order_outcomes", lambda filters: [])
    monkeypatch.setattr(
        service,
        "get_most_active_instruments",
        lambda filters, limit: [],
    )
    monkeypatch.setattr(
        service,
        "get_client_activity_rows",
        lambda filters, grouping: [],
    )
    monkeypatch.setattr(
        service,
        "get_rejection_reasons",
        lambda filters, limit: [],
    )
    monkeypatch.setattr(
        service,
        "get_client_segment_breakdown",
        lambda filters: [],
    )

    dashboard = service.build_dashboard(
        DashboardFilters(
            date_from=date(2026, 9, 1),
            date_to=date(2026, 9, 30),
            instrument_id=15,
        )
    )

    assert metadata_calls == [15]
    assert dashboard.selected_instrument.model_dump() == {
        "instrument_id": 15,
        "symbol": "DELISTED",
        "name": "Historical Instrument",
        "instrument_type": "STOCK",
        "market_name": "NYSE",
        "is_active": False,
    }
    assert dashboard.summary.model_dump() == {
        "trading_volume_usd": 0.0,
        "total_orders": 0,
        "active_clients": 0,
        "filled_orders_percentage": 0.0,
    }
    assert dashboard.trading_volume_over_time.points == []
    assert all(
        outcome.count == 0 and outcome.percentage == 0.0
        for outcome in dashboard.order_outcomes
    )
    assert dashboard.client_activity_trend.points == []
    assert dashboard.most_active_instruments == []
    assert dashboard.top_rejection_reasons == []
    assert dashboard.client_segment_breakdown == []


def test_unknown_selected_instrument_is_rejected_before_metrics(
    monkeypatch,
):
    monkeypatch.setattr(
        service,
        "get_instrument_metadata",
        lambda instrument_id: None,
    )

    with pytest.raises(service.InstrumentNotFoundError):
        service.build_dashboard(
            DashboardFilters(
                date_from=date(2026, 9, 1),
                date_to=date(2026, 9, 30),
                instrument_id=999,
            )
        )

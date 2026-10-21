from datetime import date
from decimal import Decimal

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.exc import SQLAlchemyError

import app.api.dashboard as dashboard_api
import app.main as main_api
from app.main import app
from app.schemas.dashboard import (
    ClientActivityPoint,
    ClientActivityTrend,
    ClientSegmentBreakdown,
    DashboardResponse,
    DashboardSummary,
    InstrumentActivity,
    OrderOutcome,
    RejectionReason,
    ReportingPeriod,
    SelectedInstrument,
    TimeGrouping,
    TradingVolumePoint,
    TradingVolumeTrend,
)
from app.schemas.filters import ClientSegment


client = TestClient(
    app,
    headers={
        "X-Analytics-Internal-Token": "test-analytics-internal-token"
    },
)


def test_dashboard_rejects_missing_internal_token():
    response = TestClient(app).get("/analytics/dashboard")

    assert response.status_code == 401


def test_dashboard_rejects_invalid_internal_token():
    response = TestClient(app).get(
        "/analytics/dashboard",
        headers={"X-Analytics-Internal-Token": "wrong-token"},
    )

    assert response.status_code == 401


def _mock_dashboard_response() -> DashboardResponse:
    return DashboardResponse(
        summary=DashboardSummary(
            trading_volume_usd=100000.50,
            total_orders=120,
            active_clients=18,
            filled_orders_percentage=75.0,
        ),
        trading_volume_over_time=TradingVolumeTrend(
            grouping=TimeGrouping.WEEKLY,
            points=[
                TradingVolumePoint(
                    period="2026-09-01",
                    volume_usd=25000.25,
                )
            ],
        ),
        order_outcomes=[
            OrderOutcome(
                status="FILLED",
                count=90,
                percentage=75.0,
            ),
            OrderOutcome(
                status="REJECTED",
                count=15,
                percentage=12.5,
            ),
            OrderOutcome(
                status="CANCELLED",
                count=5,
                percentage=4.17,
            ),
            OrderOutcome(
                status="ACCEPTED",
                count=5,
                percentage=4.17,
            ),
            OrderOutcome(
                status="SUBMITTED",
                count=5,
                percentage=4.17,
            ),
        ],
        most_active_instruments=[
            InstrumentActivity(
                symbol="AAPL",
                order_count=40,
            )
        ],
        client_activity_trend=ClientActivityTrend(
            grouping=TimeGrouping.WEEKLY,
            points=[
                ClientActivityPoint(
                    period="2026-09-01",
                    active_clients=12,
                )
            ],
        ),
        top_rejection_reasons=[
            RejectionReason(
                reason="INSUFFICIENT_CASH",
                count=8,
            )
        ],
        client_segment_breakdown=[
            ClientSegmentBreakdown(
                segment=ClientSegment.INACTIVE,
                total_clients=4,
                active_clients=0,
                order_count=0,
                trading_volume_usd=0.0,
            ),
            ClientSegmentBreakdown(
                segment=ClientSegment.OCCASIONAL,
                total_clients=3,
                active_clients=2,
                order_count=5,
                trading_volume_usd=100.25,
            ),
            ClientSegmentBreakdown(
                segment=ClientSegment.REGULAR,
                total_clients=2,
                active_clients=1,
                order_count=12,
                trading_volume_usd=500.5,
            ),
            ClientSegmentBreakdown(
                segment=ClientSegment.FREQUENT,
                total_clients=1,
                active_clients=1,
                order_count=25,
                trading_volume_usd=1000.75,
            ),
        ],
        reporting_period=ReportingPeriod(
            date_from=date(2026, 9, 1),
            date_to=date(2026, 9, 30),
        ),
    )


def test_health_endpoint():
    response = client.get("/health")

    assert response.status_code == 200

    assert response.json() == {
        "status": "healthy",
        "service": "analytics",
    }
    
def test_ready_endpoint_when_database_is_available(monkeypatch):
    monkeypatch.setattr(
        main_api,
        "get_reporting_status",
        lambda: {
            "in_recovery": True,
            "receiver_status": "streaming",
            "received_lsn": "0/10",
            "replayed_lsn": "0/10",
            "replay_pending": False,
            "last_replay_timestamp": None,
            "receive_start_lsn": "0/1",
            "latest_end_lsn": "0/10",
            "ready": True,
        },
    )

    response = client.get("/ready")

    assert response.status_code == 200

    assert response.json() == {
        "status": "ready",
        "service": "analytics",
        "database": "connected",
        "reporting": {
            "in_recovery": True,
            "receiver_status": "streaming",
            "received_lsn": "0/10",
            "replayed_lsn": "0/10",
            "replay_pending": False,
            "last_replay_timestamp": None,
            "receive_start_lsn": "0/1",
            "latest_end_lsn": "0/10",
            "ready": True,
        },
    }


def test_ready_endpoint_when_database_is_unavailable(monkeypatch):
    monkeypatch.setattr(
        main_api,
        "get_reporting_status",
        lambda: (_ for _ in ()).throw(SQLAlchemyError("offline")),
    )

    response = client.get("/ready")

    assert response.status_code == 503

    assert response.json() == {
        "status": "not_ready",
        "service": "analytics",
        "database": "unavailable",
    }


def test_ready_endpoint_when_replica_is_not_safe(monkeypatch):
    monkeypatch.setattr(
        main_api,
        "get_reporting_status",
        lambda: {
            "in_recovery": False,
            "receiver_status": "disconnected",
            "received_lsn": None,
            "replayed_lsn": None,
            "replay_pending": False,
            "last_replay_timestamp": None,
            "receive_start_lsn": None,
            "latest_end_lsn": None,
            "ready": False,
        },
    )

    response = client.get("/ready")

    assert response.status_code == 503
    assert response.json()["reporting"]["in_recovery"] is False


def test_dashboard_endpoint(monkeypatch):
    monkeypatch.setattr(
        dashboard_api,
        "build_dashboard",
        lambda filters: _mock_dashboard_response(),
    )

    response = client.get("/analytics/dashboard")

    assert response.status_code == 200

    body = response.json()

    assert set(body) == {
        "summary",
        "trading_volume_over_time",
        "order_outcomes",
        "most_active_instruments",
        "client_activity_trend",
        "top_rejection_reasons",
        "client_segment_breakdown",
        "selected_instrument",
        "reporting_period",
    }
    assert set(body["summary"]) == {
        "trading_volume_usd",
        "total_orders",
        "active_clients",
        "filled_orders_percentage",
    }
    assert body["summary"]["total_orders"] == 120
    assert body["summary"]["active_clients"] == 18
    assert body["summary"]["filled_orders_percentage"] == 75.0

    assert (
        body["trading_volume_over_time"]["grouping"]
        == "WEEKLY"
    )

    assert body["order_outcomes"][0]["status"] == "FILLED"

    assert (
        body["most_active_instruments"][0]["symbol"]
        == "AAPL"
    )

    assert (
        body["client_activity_trend"]["points"][0]
        ["active_clients"]
        == 12
    )

    assert (
        body["top_rejection_reasons"][0]["reason"]
        == "INSUFFICIENT_CASH"
    )

    assert len(body["client_segment_breakdown"]) == 4
    assert body["client_segment_breakdown"][0] == {
        "segment": "INACTIVE",
        "total_clients": 4,
        "active_clients": 0,
        "order_count": 0,
        "trading_volume_usd": 0.0,
    }
    assert body["selected_instrument"] is None
    assert body["reporting_period"] == {
        "date_from": "2026-09-01",
        "date_to": "2026-09-30",
        "timezone": "UTC",
    }


def test_dashboard_returns_selected_instrument_metadata(monkeypatch):
    response = _mock_dashboard_response()
    response.selected_instrument = SelectedInstrument(
        instrument_id=15,
        symbol="AAPL",
        name="Apple Inc.",
        instrument_type="STOCK",
        market_name="NASDAQ",
        is_active=True,
    )
    monkeypatch.setattr(
        dashboard_api,
        "build_dashboard",
        lambda filters: response,
    )

    result = client.get(
        "/analytics/dashboard",
        params={"instrument_id": 15},
    )

    assert result.status_code == 200
    assert result.json()["selected_instrument"] == {
        "instrument_id": 15,
        "symbol": "AAPL",
        "name": "Apple Inc.",
        "instrument_type": "STOCK",
        "market_name": "NASDAQ",
        "is_active": True,
    }


def test_dashboard_accepts_valid_filters(monkeypatch):
    captured_filters = {}

    def fake_build_dashboard(filters):
        captured_filters["filters"] = filters

        return _mock_dashboard_response()

    monkeypatch.setattr(
        dashboard_api,
        "build_dashboard",
        fake_build_dashboard,
    )

    response = client.get(
        "/analytics/dashboard",
        params={
            "date_from": "2026-09-01",
            "date_to": "2026-09-30",
            "asset_type": "STOCK",
            "market": "NASDAQ",
            "instrument_id": 15,
            "order_status": "FILLED",
            "client_segment": "REGULAR",
        },
    )

    assert response.status_code == 200

    filters = captured_filters["filters"]

    assert filters.date_from == date(2026, 9, 1)
    assert filters.date_to == date(2026, 9, 30)
    assert filters.asset_type.value == "STOCK"
    assert filters.market == "NASDAQ"
    assert filters.instrument_id == 15
    assert filters.order_status.value == "FILLED"
    assert filters.client_segment.value == "REGULAR"


def test_dashboard_rejects_invalid_asset_type():
    response = client.get(
        "/analytics/dashboard",
        params={
            "asset_type": "BOND",
        },
    )

    assert response.status_code == 422


def test_dashboard_rejects_invalid_order_status():
    response = client.get(
        "/analytics/dashboard",
        params={
            "order_status": "PENDING",
        },
    )

    assert response.status_code == 422


def test_dashboard_rejects_invalid_client_segment():
    response = client.get(
        "/analytics/dashboard",
        params={
            "client_segment": "VERY_ACTIVE",
        },
    )

    assert response.status_code == 422


def test_dashboard_returns_404_for_unknown_instrument(monkeypatch):
    def raise_not_found(filters):
        raise dashboard_api.InstrumentNotFoundError(999)

    monkeypatch.setattr(
        dashboard_api,
        "build_dashboard",
        raise_not_found,
    )

    response = client.get(
        "/analytics/dashboard",
        params={"instrument_id": 999},
    )

    assert response.status_code == 404
    assert response.json() == {
        "detail": "Instrument 999 was not found"
    }


@pytest.mark.parametrize("instrument_id", ["not-an-id", 0, -1])
def test_dashboard_rejects_invalid_instrument_id(instrument_id):
    response = client.get(
        "/analytics/dashboard",
        params={"instrument_id": instrument_id},
    )

    assert response.status_code == 422


def test_dashboard_rejects_invalid_date_range(monkeypatch):
    monkeypatch.setattr(
        dashboard_api,
        "build_dashboard",
        lambda filters: _mock_dashboard_response(),
    )

    response = client.get(
        "/analytics/dashboard",
        params={
            "date_from": "2026-10-01",
            "date_to": "2026-09-01",
        },
    )

    assert response.status_code == 422


def test_dashboard_rejects_invalid_effective_date_range(monkeypatch):
    monkeypatch.setattr(
        dashboard_api,
        "build_dashboard",
        lambda filters: (_ for _ in ()).throw(
            dashboard_api.InvalidDateRangeError()
        ),
    )

    response = client.get(
        "/analytics/dashboard",
        params={"date_from": "2026-10-21"},
    )

    assert response.status_code == 422


def test_dashboard_returns_503_when_reporting_database_fails(monkeypatch):
    monkeypatch.setattr(
        dashboard_api,
        "build_dashboard",
        lambda filters: (_ for _ in ()).throw(
            SQLAlchemyError("database details must not leak")
        ),
    )

    response = client.get("/analytics/dashboard")

    assert response.status_code == 503
    assert response.json() == {
        "detail": "Analytics reporting is temporarily unavailable"
    }
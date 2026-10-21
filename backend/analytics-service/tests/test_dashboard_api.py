from datetime import date
from decimal import Decimal

from fastapi.testclient import TestClient

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
    TimeGrouping,
    TradingVolumePoint,
    TradingVolumeTrend,
)
from app.schemas.filters import ClientSegment


client = TestClient(app)


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
        "test_database_connection",
        lambda: True,
    )

    response = client.get("/ready")

    assert response.status_code == 200

    assert response.json() == {
        "status": "ready",
        "service": "analytics",
        "database": "connected",
    }


def test_ready_endpoint_when_database_is_unavailable(monkeypatch):
    monkeypatch.setattr(
        main_api,
        "test_database_connection",
        lambda: False,
    )

    response = client.get("/ready")

    assert response.status_code == 503

    assert response.json() == {
        "status": "not_ready",
        "service": "analytics",
        "database": "unavailable",
    }


def test_dashboard_endpoint(monkeypatch):
    monkeypatch.setattr(
        dashboard_api,
        "build_dashboard",
        lambda filters: _mock_dashboard_response(),
    )

    response = client.get("/analytics/dashboard")

    assert response.status_code == 200

    body = response.json()

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
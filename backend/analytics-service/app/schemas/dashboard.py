from enum import Enum
from typing import List

from pydantic import BaseModel

from app.schemas.filters import OrderStatus


class TimeGrouping(str, Enum):
    DAILY = "DAILY"
    WEEKLY = "WEEKLY"
    MONTHLY = "MONTHLY"


class DashboardSummary(BaseModel):
    trading_volume_usd: float
    total_orders: int
    active_clients: int
    filled_orders_percentage: float


class TradingVolumePoint(BaseModel):
    period: str
    volume_usd: float


class TradingVolumeTrend(BaseModel):
    grouping: TimeGrouping
    points: List[TradingVolumePoint]


class OrderOutcome(BaseModel):
    status: OrderStatus
    count: int
    percentage: float


class InstrumentActivity(BaseModel):
    symbol: str
    order_count: int


class ClientActivityPoint(BaseModel):
    period: str
    active_clients: int


class ClientActivityTrend(BaseModel):
    grouping: TimeGrouping
    points: List[ClientActivityPoint]


class RejectionReason(BaseModel):
    reason: str
    count: int


class DashboardResponse(BaseModel):
    summary: DashboardSummary
    trading_volume_over_time: TradingVolumeTrend
    order_outcomes: List[OrderOutcome]
    most_active_instruments: List[InstrumentActivity]
    client_activity_trend: ClientActivityTrend
    top_rejection_reasons: List[RejectionReason]
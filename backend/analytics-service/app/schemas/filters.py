from datetime import date
from enum import Enum
from typing import Optional

from pydantic import BaseModel, Field, model_validator


class AssetType(str, Enum):
    STOCK = "STOCK"
    CRYPTO = "CRYPTO"
    FOREX = "FOREX"


class OrderStatus(str, Enum):
    SUBMITTED = "SUBMITTED"
    ACCEPTED = "ACCEPTED"
    FILLED = "FILLED"
    REJECTED = "REJECTED"
    CANCELLED = "CANCELLED"
    EXPIRED = "EXPIRED"


class ClientSegment(str, Enum):
    INACTIVE = "INACTIVE"
    OCCASIONAL = "OCCASIONAL"
    REGULAR = "REGULAR"
    FREQUENT = "FREQUENT"


class DashboardFilters(BaseModel):
    date_from: Optional[date] = None
    date_to: Optional[date] = None

    asset_type: Optional[AssetType] = None

    market: Optional[str] = Field(
        default=None,
        min_length=1,
        max_length=100,
    )

    instrument_id: Optional[int] = Field(default=None, gt=0)

    order_status: Optional[OrderStatus] = None

    client_segment: Optional[ClientSegment] = None

    @model_validator(mode="after")
    def validate_date_range(self):
        if (
            self.date_from is not None
            and self.date_to is not None
            and self.date_from > self.date_to
        ):
            raise ValueError("date_from cannot be after date_to")

        return self
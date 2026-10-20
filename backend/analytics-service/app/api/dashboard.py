from datetime import date
from typing import Optional

from fastapi import APIRouter, HTTPException, Query

from app.schemas.dashboard import DashboardResponse
from app.schemas.filters import (
    AssetType,
    ClientSegment,
    DashboardFilters,
    OrderStatus,
)
from app.services.dashboard_service import (
    InvalidDateRangeError,
    InstrumentNotFoundError,
    build_dashboard,
)


router = APIRouter(
    prefix="/analytics/dashboard",
    tags=["Analytics Dashboard"],
)


@router.get(
    "",
    response_model=DashboardResponse,
)
def get_dashboard(
    date_from: Optional[date] = Query(default=None),
    date_to: Optional[date] = Query(default=None),
    asset_type: Optional[AssetType] = Query(default=None),
    market: Optional[str] = Query(
        default=None,
        min_length=1,
        max_length=100,
    ),
    instrument_id: Optional[int] = Query(default=None, gt=0),
    order_status: Optional[OrderStatus] = Query(default=None),
    client_segment: Optional[ClientSegment] = Query(default=None),
) -> DashboardResponse:
    if (
        date_from is not None
        and date_to is not None
        and date_from > date_to
    ):
        raise HTTPException(
            status_code=422,
            detail="date_from cannot be after date_to",
        )

    filters = DashboardFilters(
        date_from=date_from,
        date_to=date_to,
        asset_type=asset_type,
        market=market,
        instrument_id=instrument_id,
        order_status=order_status,
        client_segment=client_segment,
    )

    try:
        return build_dashboard(filters)
    except InvalidDateRangeError as error:
        raise HTTPException(
            status_code=422,
            detail=str(error),
        ) from error
    except InstrumentNotFoundError as error:
        raise HTTPException(
            status_code=404,
            detail=str(error),
        ) from error
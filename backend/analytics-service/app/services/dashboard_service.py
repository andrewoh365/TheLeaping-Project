from datetime import date, timedelta
from typing import Dict, List

from app.repositories.analytics_repository import (
    get_active_clients,
    get_client_activity_rows,
    get_fill_rate_counts,
    get_most_active_instruments,
    get_order_outcomes,
    get_rejection_reasons,
    get_total_orders,
    get_trading_volume,
    get_trading_volume_rows,
)
from app.schemas.dashboard import (
    ClientActivityPoint,
    ClientActivityTrend,
    DashboardResponse,
    DashboardSummary,
    InstrumentActivity,
    OrderOutcome,
    RejectionReason,
    TimeGrouping,
    TradingVolumePoint,
    TradingVolumeTrend,
)
from app.schemas.filters import DashboardFilters, OrderStatus


DEFAULT_DATE_RANGE_DAYS = 90
TOP_INSTRUMENT_LIMIT = 5
TOP_REJECTION_REASON_LIMIT = 5


def _apply_default_date_range(
    filters: DashboardFilters,
) -> DashboardFilters:
    values = filters.model_dump()

    date_from = filters.date_from
    date_to = filters.date_to

    if date_from is None and date_to is None:
        date_to = date.today()
        date_from = date_to - timedelta(
            days=DEFAULT_DATE_RANGE_DAYS - 1
        )

    elif date_from is None:
        date_from = date_to - timedelta(
            days=DEFAULT_DATE_RANGE_DAYS - 1
        )

    elif date_to is None:
        date_to = date.today()

    values["date_from"] = date_from
    values["date_to"] = date_to

    return DashboardFilters(**values)


def _determine_time_grouping(
    filters: DashboardFilters,
) -> TimeGrouping:
    number_of_days = (
        filters.date_to - filters.date_from
    ).days + 1

    if number_of_days <= 31:
        return TimeGrouping.DAILY

    if number_of_days <= 180:
        return TimeGrouping.WEEKLY

    return TimeGrouping.MONTHLY


def _build_summary(
    filters: DashboardFilters,
) -> DashboardSummary:
    total_orders = get_total_orders(filters)
    trading_volume = get_trading_volume(filters)
    active_clients = get_active_clients(filters)

    fill_counts = get_fill_rate_counts(filters)

    total_for_fill_rate = fill_counts["total_orders"]
    filled_orders = fill_counts["filled_orders"]

    if total_for_fill_rate == 0:
        fill_percentage = 0.0
    else:
        fill_percentage = round(
            (filled_orders / total_for_fill_rate) * 100,
            2,
        )

    return DashboardSummary(
        trading_volume_usd=round(
            float(trading_volume),
            2,
        ),
        total_orders=total_orders,
        active_clients=active_clients,
        filled_orders_percentage=fill_percentage,
    )


def _build_trading_volume_trend(
    filters: DashboardFilters,
    grouping: TimeGrouping,
) -> TradingVolumeTrend:
    rows = get_trading_volume_rows(filters, grouping)

    if not rows:
        return TradingVolumeTrend(
            grouping=grouping,
            points=[],
        )

    points = [
        TradingVolumePoint(
            period=row["period"],
            volume_usd=round(
                float(row["total_usd_value"]),
                2,
            ),
        )
        for row in rows
    ]

    return TradingVolumeTrend(
        grouping=grouping,
        points=points,
    )


def _build_order_outcomes(
    filters: DashboardFilters,
) -> List[OrderOutcome]:
    rows = get_order_outcomes(filters)

    counts: Dict[str, int] = {
        row["order_status"]: row["order_count"]
        for row in rows
    }

    total_orders = sum(counts.values())

    outcomes: List[OrderOutcome] = []

    for status in OrderStatus:
        count = counts.get(status.value, 0)

        if total_orders == 0:
            percentage = 0.0
        else:
            percentage = round(
                (count / total_orders) * 100,
                2,
            )

        outcomes.append(
            OrderOutcome(
                status=status,
                count=count,
                percentage=percentage,
            )
        )

    return outcomes


def _build_most_active_instruments(
    filters: DashboardFilters,
) -> List[InstrumentActivity]:
    rows = get_most_active_instruments(
        filters,
        limit=TOP_INSTRUMENT_LIMIT,
    )

    return [
        InstrumentActivity(
            symbol=row["symbol"],
            order_count=row["order_count"],
        )
        for row in rows[:TOP_INSTRUMENT_LIMIT]
    ]


def _build_client_activity_trend(
    filters: DashboardFilters,
    grouping: TimeGrouping,
) -> ClientActivityTrend:
    rows = get_client_activity_rows(filters, grouping)

    if not rows:
        return ClientActivityTrend(
            grouping=grouping,
            points=[],
        )

    points = [
        ClientActivityPoint(
            period=row["period"],
            active_clients=int(row["active_clients"]),
        )
        for row in rows
    ]

    return ClientActivityTrend(
        grouping=grouping,
        points=points,
    )


def _build_rejection_reasons(
    filters: DashboardFilters,
) -> List[RejectionReason]:
    rows = get_rejection_reasons(
        filters,
        limit=TOP_REJECTION_REASON_LIMIT,
    )

    return [
        RejectionReason(
            reason=row["rejection_reason"],
            count=row["rejection_count"],
        )
        for row in rows[:TOP_REJECTION_REASON_LIMIT]
    ]


def build_dashboard(
    filters: DashboardFilters,
) -> DashboardResponse:
    effective_filters = _apply_default_date_range(
        filters
    )

    grouping = _determine_time_grouping(
        effective_filters
    )

    return DashboardResponse(
        summary=_build_summary(
            effective_filters
        ),
        trading_volume_over_time=(
            _build_trading_volume_trend(
                effective_filters,
                grouping,
            )
        ),
        order_outcomes=_build_order_outcomes(
            effective_filters
        ),
        most_active_instruments=(
            _build_most_active_instruments(
                effective_filters
            )
        ),
        client_activity_trend=(
            _build_client_activity_trend(
                effective_filters,
                grouping,
            )
        ),
        top_rejection_reasons=(
            _build_rejection_reasons(
                effective_filters
            )
        ),
    )
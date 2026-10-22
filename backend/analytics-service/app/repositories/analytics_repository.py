
from datetime import date, datetime, time, timedelta, timezone
from decimal import Decimal
from typing import Dict, List, Optional, Tuple

from sqlalchemy import text

from app.database.connection import engine
from app.schemas.dashboard import TimeGrouping
from app.schemas.filters import DashboardFilters


_CLIENT_SEGMENT_SQL = """
    SELECT
        p.portfolio_id,
        CASE
            WHEN COUNT(segment_orders.order_id) = 0
                THEN 'INACTIVE'
            WHEN COUNT(segment_orders.order_id) BETWEEN 1 AND 9
                THEN 'OCCASIONAL'
            WHEN COUNT(segment_orders.order_id) BETWEEN 10 AND 19
                THEN 'REGULAR'
            ELSE 'FREQUENT'
        END AS client_segment
    FROM portfolios p
    LEFT JOIN trade_orders segment_orders
        ON segment_orders.portfolio_id = p.portfolio_id
        AND segment_orders.submitted_at >= :segment_start
        AND segment_orders.submitted_at < :segment_end
    GROUP BY p.portfolio_id
"""


def _segment_window_params(
    filters: DashboardFilters,
) -> Dict[str, object]:
    if filters.date_to is None:
        raise ValueError(
            "date_to is required when filtering by client segment"
        )

    return {
        "segment_start": _utc_midnight(
            filters.date_to - timedelta(days=89)
        ),
        "segment_end": _utc_midnight(
            filters.date_to + timedelta(days=1)
        ),
    }


def _utc_midnight(day: date) -> datetime:
    return datetime.combine(
        day,
        time.min,
        tzinfo=timezone.utc,
    )


def _period_expression(
    timestamp_column: str,
    grouping: TimeGrouping,
) -> str:
    truncation = {
        TimeGrouping.DAILY: "day",
        TimeGrouping.WEEKLY: "week",
        TimeGrouping.MONTHLY: "month",
    }[grouping]

    utc_timestamp = f"{timestamp_column} AT TIME ZONE 'UTC'"

    return (
        "TO_CHAR("
        f"DATE_TRUNC('{truncation}', {utc_timestamp}), "
        "'YYYY-MM-DD')"
    )


def _build_filters(
    filters: DashboardFilters,
    timestamp_column: str,
    include_order_status: bool = True,
    include_client_segment: bool = True,
) -> Tuple[str, Dict[str, object]]:
    clauses = ["1 = 1"]
    params: Dict[str, object] = {}

    if filters.date_from is not None:
        clauses.append(f"{timestamp_column} >= :date_from")
        params["date_from"] = _utc_midnight(filters.date_from)

    if filters.date_to is not None:
        clauses.append(f"{timestamp_column} < :date_to")
        params["date_to"] = _utc_midnight(
            filters.date_to + timedelta(days=1)
        )

    if filters.asset_type is not None:
        clauses.append("i.instrument_type = :asset_type")
        params["asset_type"] = filters.asset_type.value

    if filters.instrument_id is not None:
        clauses.append("o.instrument_id = :instrument_id")
        params["instrument_id"] = filters.instrument_id

    if filters.market is not None:
        clauses.append("m.market_name = :market")
        params["market"] = filters.market

    if include_order_status and filters.order_status is not None:
        clauses.append("o.order_status = :order_status")
        params["order_status"] = filters.order_status.value

    if include_client_segment and filters.client_segment is not None:
        clauses.append(
            "o.portfolio_id IN ("
            "SELECT client_segments.portfolio_id "
            f"FROM ({_CLIENT_SEGMENT_SQL}) AS client_segments "
            "WHERE client_segments.client_segment = :client_segment"
            ")"
        )
        params.update(_segment_window_params(filters))
        params["client_segment"] = filters.client_segment.value

    return " AND ".join(clauses), params


def get_instrument_metadata(
    instrument_id: int,
) -> Optional[Dict[str, object]]:
    query = """
        SELECT
            i.instrument_id,
            i.symbol,
            i.name,
            i.instrument_type,
            m.market_name,
            i.is_active
        FROM instruments i
        JOIN markets m
            ON m.market_id = i.market_id
        WHERE i.instrument_id = :instrument_id
    """

    with engine.connect() as connection:
        row = connection.execute(
            text(query),
            {"instrument_id": instrument_id},
        ).mappings().one_or_none()

        return None if row is None else dict(row)


def get_total_orders(filters: DashboardFilters) -> int:
    where_clause, params = _build_filters(
        filters,
        timestamp_column="o.submitted_at",
    )

    query = f"""
        SELECT COUNT(*)
        FROM trade_orders o
        JOIN instruments i
            ON i.instrument_id = o.instrument_id
        JOIN markets m
            ON m.market_id = i.market_id
        WHERE {where_clause}
    """

    with engine.connect() as connection:
        return connection.execute(
            text(query),
            params,
        ).scalar_one()


def get_trading_volume(filters: DashboardFilters) -> Decimal:
    where_clause, params = _build_filters(
        filters,
        timestamp_column="t.executed_at",
    )

    query = f"""
        SELECT COALESCE(SUM(t.total_usd_value), 0)
        FROM trades t
        JOIN trade_orders o
            ON o.order_id = t.order_id
        JOIN instruments i
            ON i.instrument_id = o.instrument_id
        JOIN markets m
            ON m.market_id = i.market_id
        WHERE {where_clause}
    """

    with engine.connect() as connection:
        result = connection.execute(
            text(query),
            params,
        ).scalar_one()

        return Decimal(result)


def get_active_clients(filters: DashboardFilters) -> int:
    where_clause, params = _build_filters(
        filters,
        timestamp_column="o.submitted_at",
    )

    query = f"""
        SELECT COUNT(DISTINCT p.customer_id)
        FROM trade_orders o
        JOIN portfolios p
            ON p.portfolio_id = o.portfolio_id
        JOIN instruments i
            ON i.instrument_id = o.instrument_id
        JOIN markets m
            ON m.market_id = i.market_id
        WHERE {where_clause}
    """

    with engine.connect() as connection:
        return connection.execute(
            text(query),
            params,
        ).scalar_one()


def get_fill_rate_counts(
    filters: DashboardFilters,
) -> Dict[str, int]:
    where_clause, params = _build_filters(
        filters,
        timestamp_column="o.submitted_at",
    )

    query = f"""
        SELECT
            COUNT(*) AS total_orders,
            COUNT(*) FILTER (
                WHERE o.order_status = 'FILLED'
            ) AS filled_orders
        FROM trade_orders o
        JOIN instruments i
            ON i.instrument_id = o.instrument_id
        JOIN markets m
            ON m.market_id = i.market_id
        WHERE {where_clause}
    """

    with engine.connect() as connection:
        row = connection.execute(
            text(query),
            params,
        ).mappings().one()

        return {
            "total_orders": row["total_orders"],
            "filled_orders": row["filled_orders"],
        }


def get_trading_volume_rows(
    filters: DashboardFilters,
    grouping: TimeGrouping,
) -> List[Dict[str, object]]:
    where_clause, params = _build_filters(
        filters,
        timestamp_column="t.executed_at",
    )

    period_expression = _period_expression(
        "t.executed_at",
        grouping,
    )

    query = f"""
        SELECT
            {period_expression} AS period,
            COALESCE(SUM(t.total_usd_value), 0) AS total_usd_value
        FROM trades t
        JOIN trade_orders o
            ON o.order_id = t.order_id
        JOIN instruments i
            ON i.instrument_id = o.instrument_id
        JOIN markets m
            ON m.market_id = i.market_id
        WHERE {where_clause}
        GROUP BY 1
        ORDER BY 1 ASC
    """

    with engine.connect() as connection:
        rows = connection.execute(
            text(query),
            params,
        ).mappings().all()

        return [dict(row) for row in rows]


def get_client_segment_breakdown(
    filters: DashboardFilters,
) -> List[Dict[str, object]]:
    order_where_clause, order_params = _build_filters(
        filters,
        timestamp_column="o.submitted_at",
        include_order_status=True,
        include_client_segment=False,
    )
    volume_where_clause, volume_params = _build_filters(
        filters,
        timestamp_column="t.executed_at",
        include_order_status=True,
        include_client_segment=False,
    )

    params: Dict[str, object] = {
        **order_params,
        **volume_params,
        **_segment_window_params(filters),
    }

    segment_filter = ""
    if filters.client_segment is not None:
        segment_filter = (
            "WHERE segment_values.segment = :client_segment"
        )
        params["client_segment"] = filters.client_segment.value

    query = f"""
        WITH client_segments AS (
            {_CLIENT_SEGMENT_SQL}
        ),
        segment_values(segment) AS (
            VALUES
                ('INACTIVE'),
                ('OCCASIONAL'),
                ('REGULAR'),
                ('FREQUENT')
        ),
        segment_populations AS (
            SELECT
                cs.client_segment AS segment,
                COUNT(DISTINCT p.customer_id) AS total_clients
            FROM client_segments cs
            JOIN portfolios p
                ON p.portfolio_id = cs.portfolio_id
            GROUP BY cs.client_segment
        ),
        order_metrics AS (
            SELECT
                cs.client_segment AS segment,
                COUNT(DISTINCT p.customer_id) AS active_clients,
                COUNT(o.order_id) AS order_count
            FROM client_segments cs
            JOIN portfolios p
                ON p.portfolio_id = cs.portfolio_id
            JOIN trade_orders o
                ON o.portfolio_id = cs.portfolio_id
            JOIN instruments i
                ON i.instrument_id = o.instrument_id
            JOIN markets m
                ON m.market_id = i.market_id
            WHERE {order_where_clause}
            GROUP BY cs.client_segment
        ),
        volume_metrics AS (
            SELECT
                cs.client_segment AS segment,
                COALESCE(SUM(t.total_usd_value), 0)
                    AS trading_volume_usd
            FROM client_segments cs
            JOIN trade_orders o
                ON o.portfolio_id = cs.portfolio_id
            JOIN trades t
                ON t.order_id = o.order_id
            JOIN instruments i
                ON i.instrument_id = o.instrument_id
            JOIN markets m
                ON m.market_id = i.market_id
            WHERE {volume_where_clause}
            GROUP BY cs.client_segment
        )
        SELECT
            segment_values.segment,
            COALESCE(segment_populations.total_clients, 0)
                AS total_clients,
            COALESCE(order_metrics.active_clients, 0)
                AS active_clients,
            COALESCE(order_metrics.order_count, 0)
                AS order_count,
            COALESCE(volume_metrics.trading_volume_usd, 0)
                AS trading_volume_usd
        FROM segment_values
        LEFT JOIN segment_populations
            ON segment_populations.segment = segment_values.segment
        LEFT JOIN order_metrics
            ON order_metrics.segment = segment_values.segment
        LEFT JOIN volume_metrics
            ON volume_metrics.segment = segment_values.segment
        {segment_filter}
        ORDER BY CASE segment_values.segment
            WHEN 'INACTIVE' THEN 1
            WHEN 'OCCASIONAL' THEN 2
            WHEN 'REGULAR' THEN 3
            WHEN 'FREQUENT' THEN 4
        END
    """

    with engine.connect() as connection:
        rows = connection.execute(
            text(query),
            params,
        ).mappings().all()

        return [dict(row) for row in rows]


def get_order_outcomes(
    filters: DashboardFilters,
) -> List[Dict[str, object]]:
    where_clause, params = _build_filters(
        filters,
        timestamp_column="o.submitted_at",
    )

    query = f"""
        SELECT
            o.order_status,
            COUNT(*) AS order_count
        FROM trade_orders o
        JOIN instruments i
            ON i.instrument_id = o.instrument_id
        JOIN markets m
            ON m.market_id = i.market_id
        WHERE {where_clause}
        GROUP BY o.order_status
        ORDER BY order_count DESC
    """

    with engine.connect() as connection:
        rows = connection.execute(
            text(query),
            params,
        ).mappings().all()

        return [dict(row) for row in rows]


def get_most_active_instruments(
    filters: DashboardFilters,
    limit: int,
) -> List[Dict[str, object]]:
    where_clause, params = _build_filters(
        filters,
        timestamp_column="o.submitted_at",
    )

    query = f"""
        SELECT
            i.symbol,
            COUNT(*) AS order_count
        FROM trade_orders o
        JOIN instruments i
            ON i.instrument_id = o.instrument_id
        JOIN markets m
            ON m.market_id = i.market_id
        WHERE {where_clause}
        GROUP BY i.instrument_id, i.symbol
        ORDER BY order_count DESC, i.symbol ASC
        LIMIT :result_limit
    """

    params["result_limit"] = limit

    with engine.connect() as connection:
        rows = connection.execute(
            text(query),
            params,
        ).mappings().all()

        return [dict(row) for row in rows]


def get_client_activity_rows(
    filters: DashboardFilters,
    grouping: TimeGrouping,
) -> List[Dict[str, object]]:
    where_clause, params = _build_filters(
        filters,
        timestamp_column="o.submitted_at",
    )

    period_expression = _period_expression(
        "o.submitted_at",
        grouping,
    )

    query = f"""
        SELECT
            {period_expression} AS period,
            COUNT(DISTINCT p.customer_id) AS active_clients
        FROM trade_orders o
        JOIN portfolios p
            ON p.portfolio_id = o.portfolio_id
        JOIN instruments i
            ON i.instrument_id = o.instrument_id
        JOIN markets m
            ON m.market_id = i.market_id
        WHERE {where_clause}
        GROUP BY 1
        ORDER BY 1 ASC
    """

    with engine.connect() as connection:
        rows = connection.execute(
            text(query),
            params,
        ).mappings().all()

        return [dict(row) for row in rows]


def get_rejection_reasons(
    filters: DashboardFilters,
    limit: int,
) -> List[Dict[str, object]]:
    where_clause, params = _build_filters(
        filters,
        timestamp_column="o.submitted_at",
    )

    query = f"""
        SELECT
            o.rejection_reason,
            COUNT(*) AS rejection_count
        FROM trade_orders o
        JOIN instruments i
            ON i.instrument_id = o.instrument_id
        JOIN markets m
            ON m.market_id = i.market_id
        WHERE {where_clause}
          AND o.order_status = 'REJECTED'
          AND o.rejection_reason IS NOT NULL
          AND BTRIM(o.rejection_reason) <> ''
        GROUP BY o.rejection_reason
        ORDER BY rejection_count DESC, o.rejection_reason ASC
        LIMIT :result_limit
    """

    params["result_limit"] = limit

    with engine.connect() as connection:
        rows = connection.execute(
            text(query),
            params,
        ).mappings().all()

        return [dict(row) for row in rows]

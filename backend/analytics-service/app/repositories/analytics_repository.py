from decimal import Decimal
from typing import Dict, List, Tuple

from sqlalchemy import text

from app.database.connection import engine
from app.schemas.filters import DashboardFilters


def _build_filters(
    filters: DashboardFilters,
    timestamp_column: str,
    include_order_status: bool = True,
) -> Tuple[str, Dict[str, object]]:
    clauses = ["1 = 1"]
    params: Dict[str, object] = {}

    if filters.date_from is not None:
        clauses.append(f"{timestamp_column} >= :date_from")
        params["date_from"] = filters.date_from

    if filters.date_to is not None:
        clauses.append(
            f"{timestamp_column} < (:date_to + INTERVAL '1 day')"
        )
        params["date_to"] = filters.date_to

    if filters.asset_type is not None:
        clauses.append("i.instrument_type = :asset_type")
        params["asset_type"] = filters.asset_type.value

    if filters.market is not None:
        clauses.append("m.market_name = :market")
        params["market"] = filters.market

    if include_order_status and filters.order_status is not None:
        clauses.append("o.order_status = :order_status")
        params["order_status"] = filters.order_status.value

    return " AND ".join(clauses), params


def get_total_orders(filters: DashboardFilters) -> int:
    where_clause, params = _build_filters(
        filters,
        timestamp_column="o.submitted_at",
        include_order_status=True,
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
        include_order_status=False,
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
        include_order_status=True,
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
        include_order_status=False,
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
) -> List[Dict[str, object]]:
    where_clause, params = _build_filters(
        filters,
        timestamp_column="t.executed_at",
        include_order_status=False,
    )

    query = f"""
        SELECT
            t.executed_at,
            t.total_usd_value
        FROM trades t
        JOIN trade_orders o
            ON o.order_id = t.order_id
        JOIN instruments i
            ON i.instrument_id = o.instrument_id
        JOIN markets m
            ON m.market_id = i.market_id
        WHERE {where_clause}
        ORDER BY t.executed_at ASC
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
        include_order_status=False,
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
) -> List[Dict[str, object]]:
    where_clause, params = _build_filters(
        filters,
        timestamp_column="o.submitted_at",
        include_order_status=True,
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
    """

    with engine.connect() as connection:
        rows = connection.execute(
            text(query),
            params,
        ).mappings().all()

        return [dict(row) for row in rows]


def get_client_activity_rows(
    filters: DashboardFilters,
) -> List[Dict[str, object]]:
    where_clause, params = _build_filters(
        filters,
        timestamp_column="o.submitted_at",
        include_order_status=True,
    )

    query = f"""
        SELECT
            o.submitted_at,
            p.customer_id
        FROM trade_orders o
        JOIN portfolios p
            ON p.portfolio_id = o.portfolio_id
        JOIN instruments i
            ON i.instrument_id = o.instrument_id
        JOIN markets m
            ON m.market_id = i.market_id
        WHERE {where_clause}
        ORDER BY o.submitted_at ASC
    """

    with engine.connect() as connection:
        rows = connection.execute(
            text(query),
            params,
        ).mappings().all()

        return [dict(row) for row in rows]


def get_rejection_reasons(
    filters: DashboardFilters,
) -> List[Dict[str, object]]:
    where_clause, params = _build_filters(
        filters,
        timestamp_column="o.submitted_at",
        include_order_status=False,
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
    """

    with engine.connect() as connection:
        rows = connection.execute(
            text(query),
            params,
        ).mappings().all()

        return [dict(row) for row in rows]
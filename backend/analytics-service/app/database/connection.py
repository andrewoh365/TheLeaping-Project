from typing import Any, Dict

from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine

from app.core.config import settings


engine: Engine = create_engine(
    settings.database_url,
    pool_pre_ping=True,
    pool_size=settings.pool_size,
    max_overflow=settings.max_overflow,
    pool_timeout=settings.pool_timeout_seconds,
    connect_args={
        "connect_timeout": settings.connect_timeout_seconds,
        "options": (
            f"-c statement_timeout={settings.statement_timeout_ms} "
            "-c default_transaction_read_only=on"
        ),
    },
)


def get_reporting_status() -> Dict[str, Any]:
    with engine.connect() as connection:
        row = connection.execute(
            text(
                """
                SELECT
                    pg_is_in_recovery() AS in_recovery,
                    pg_last_wal_receive_lsn()::text AS received_lsn,
                    pg_last_wal_replay_lsn()::text AS replayed_lsn,
                    pg_last_xact_replay_timestamp()
                        AS last_replay_timestamp,
                    receiver.status AS receiver_status,
                    receiver.receive_start_lsn::text AS receive_start_lsn,
                    receiver.latest_end_lsn::text AS latest_end_lsn,
                    CASE
                        WHEN pg_last_wal_receive_lsn() IS NULL
                            OR pg_last_wal_replay_lsn() IS NULL
                            THEN FALSE
                        ELSE pg_wal_lsn_diff(
                            pg_last_wal_receive_lsn(),
                            pg_last_wal_replay_lsn()
                        ) > 0
                    END AS replay_pending,
                    -- latest_end_lsn is the primary's WAL end as last
                    -- reported to the receiver, so this also counts WAL
                    -- the standby has not received yet.
                    GREATEST(
                        pg_wal_lsn_diff(
                            GREATEST(
                                receiver.latest_end_lsn,
                                pg_last_wal_receive_lsn()
                            ),
                            pg_last_wal_replay_lsn()
                        ),
                        0
                    ) AS replay_lag_bytes
                FROM (SELECT 1) AS connection_check
                LEFT JOIN LATERAL (
                    SELECT
                        status,
                        receive_start_lsn,
                        latest_end_lsn
                    FROM pg_stat_wal_receiver
                    LIMIT 1
                ) AS receiver ON TRUE
                """
            )
        ).one()

    receiver_status = row.receiver_status or "disconnected"
    replay_pending = bool(row.replay_pending)
    replay_lag_bytes = (
        int(row.replay_lag_bytes)
        if row.replay_lag_bytes is not None
        else None
    )
    # Asynchronous replay is always slightly behind under steady writes, so
    # a zero-lag requirement would flap; only lag past the threshold, or an
    # unknown position, makes reporting data too stale to serve.
    ready = (
        row.in_recovery is True
        and receiver_status == "streaming"
        and replay_lag_bytes is not None
        and replay_lag_bytes <= settings.max_replay_lag_bytes
    )

    return {
        "in_recovery": row.in_recovery is True,
        "receiver_status": receiver_status,
        "received_lsn": row.received_lsn,
        "replayed_lsn": row.replayed_lsn,
        "replay_pending": replay_pending,
        "replay_lag_bytes": replay_lag_bytes,
        "last_replay_timestamp": (
            row.last_replay_timestamp.isoformat()
            if row.last_replay_timestamp is not None
            else None
        ),
        "receive_start_lsn": row.receive_start_lsn,
        "latest_end_lsn": row.latest_end_lsn,
        "ready": ready,
    }


def test_database_connection() -> bool:
    return get_reporting_status()["ready"]

import pytest
from types import SimpleNamespace

import app.database.connection as database_connection
from app.core.config import Settings


class _Result:
    def __init__(self, row):
        self.row = row

    def one(self):
        if isinstance(self.row, tuple):
            return SimpleNamespace(
                in_recovery=self.row[0],
                receiver_status=self.row[1],
                received_lsn=self.row[2],
                replayed_lsn=self.row[3],
                replay_pending=self.row[4],
                last_replay_timestamp=None,
                receive_start_lsn=None,
                latest_end_lsn=None,
            )

        return self.row


class _Connection:
    def __init__(self, row):
        self.row = row

    def __enter__(self):
        return self

    def __exit__(self, exception_type, exception, traceback):
        return False

    def execute(self, query):
        return _Result(self.row)


class _Engine:
    def __init__(self, row):
        self.row = row

    def connect(self):
        return _Connection(self.row)


def test_settings_require_reporting_database_configuration(monkeypatch):
    for name in (
        "ANALYTICS_DB_HOST",
        "ANALYTICS_DB_PORT",
        "ANALYTICS_DB_NAME",
        "ANALYTICS_DB_USERNAME",
        "ANALYTICS_DB_PASSWORD",
        "ANALYTICS_INTERNAL_TOKEN",
    ):
        monkeypatch.delenv(name, raising=False)

    with pytest.raises(ValueError, match="ANALYTICS_DB_USERNAME"):
        Settings()


def test_settings_do_not_use_primary_database_variables(monkeypatch):
    monkeypatch.setenv("DB_HOST", "primary")
    monkeypatch.setenv("DB_USERNAME", "primary_user")
    monkeypatch.setenv("DB_PASSWORD", "primary_password")
    monkeypatch.setenv("ANALYTICS_DB_HOST", "reporting")
    monkeypatch.setenv("ANALYTICS_DB_PORT", "5432")
    monkeypatch.setenv("ANALYTICS_DB_NAME", "leaping_db")
    monkeypatch.setenv("ANALYTICS_DB_USERNAME", "analytics_reader")
    monkeypatch.setenv("ANALYTICS_DB_PASSWORD", "reporting_password")
    monkeypatch.setenv(
        "ANALYTICS_INTERNAL_TOKEN",
        "internal-token",
    )

    settings = Settings()

    assert settings.db_host == "reporting"
    assert settings.db_username == "analytics_reader"
    assert "primary" not in settings.database_url
    assert "primary_user" not in settings.database_url


def test_database_connection_accepts_only_recovery_server(monkeypatch):
    monkeypatch.setattr(
        database_connection,
        "engine",
        _Engine((True, "streaming", "0/10", "0/10", False)),
    )
    assert database_connection.test_database_connection() is True

    monkeypatch.setattr(
        database_connection,
        "engine",
        _Engine((False, "streaming", "0/10", "0/10", False)),
    )
    assert database_connection.test_database_connection() is False


def test_reporting_status_rejects_disconnected_or_pending_replication(
    monkeypatch,
):
    monkeypatch.setattr(
        database_connection,
        "engine",
        _Engine((True, "disconnected", "0/20", "0/10", True)),
    )

    status = database_connection.get_reporting_status()

    assert status["in_recovery"] is True
    assert status["receiver_status"] == "disconnected"
    assert status["replay_pending"] is True
    assert status["ready"] is False

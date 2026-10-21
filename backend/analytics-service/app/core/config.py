import os
from urllib.parse import quote_plus


class Settings:
    def __init__(self):
        self.db_username = self._required("ANALYTICS_DB_USERNAME")
        self.db_password = self._required("ANALYTICS_DB_PASSWORD")
        self.db_host = self._required("ANALYTICS_DB_HOST")
        self.db_port = int(self._required("ANALYTICS_DB_PORT"))
        self.db_name = self._required("ANALYTICS_DB_NAME")
        self.internal_token = self._required(
            "ANALYTICS_INTERNAL_TOKEN"
        )

        self.pool_size = int(
            os.getenv("ANALYTICS_DB_POOL_SIZE", "2")
        )
        self.max_overflow = int(
            os.getenv("ANALYTICS_DB_MAX_OVERFLOW", "0")
        )
        self.pool_timeout_seconds = int(
            os.getenv("ANALYTICS_DB_POOL_TIMEOUT_SECONDS", "10")
        )
        self.connect_timeout_seconds = int(
            os.getenv("ANALYTICS_DB_CONNECT_TIMEOUT_SECONDS", "5")
        )
        self.statement_timeout_ms = int(
            os.getenv("ANALYTICS_DB_STATEMENT_TIMEOUT_MS", "30000")
        )
        # Default is one 16 MiB WAL segment of received-but-unreplayed WAL.
        self.max_replay_lag_bytes = int(
            os.getenv("ANALYTICS_MAX_REPLAY_LAG_BYTES", "16777216")
        )

    @staticmethod
    def _required(name: str) -> str:
        value = os.getenv(name)

        if not value or not value.strip():
            raise ValueError(
                f"{name} environment variable is required"
            )

        return value

    @property
    def database_url(self) -> str:
        username = quote_plus(self.db_username)
        password = quote_plus(self.db_password)

        return (
            f"postgresql+psycopg://{username}:{password}"
            f"@{self.db_host}:{self.db_port}/{self.db_name}"
        )


settings = Settings()

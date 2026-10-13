import os
from urllib.parse import quote_plus


class Settings:
    def __init__(self):
        self.db_username = os.getenv("DB_USERNAME")
        self.db_password = os.getenv("DB_PASSWORD")

        self.db_host = os.getenv("DB_HOST", "localhost")
        self.db_port = int(os.getenv("DB_PORT", "5432"))
        self.db_name = os.getenv("DB_NAME", "leaping_db")

        if not self.db_username:
            raise ValueError("DB_USERNAME environment variable is required")

        if not self.db_password:
            raise ValueError("DB_PASSWORD environment variable is required")

    @property
    def database_url(self) -> str:
        username = quote_plus(self.db_username)
        password = quote_plus(self.db_password)

        return (
            f"postgresql+psycopg://{username}:{password}"
            f"@{self.db_host}:{self.db_port}/{self.db_name}"
        )


settings = Settings()

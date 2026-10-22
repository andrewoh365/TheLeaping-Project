import os


os.environ.setdefault("ANALYTICS_DB_HOST", "localhost")
os.environ.setdefault("ANALYTICS_DB_PORT", "5432")
os.environ.setdefault("ANALYTICS_DB_NAME", "analytics_test")
os.environ.setdefault("ANALYTICS_DB_USERNAME", "analytics_reader")
os.environ.setdefault("ANALYTICS_DB_PASSWORD", "test_reporting_password")
os.environ.setdefault(
    "ANALYTICS_INTERNAL_TOKEN",
    "test-analytics-internal-token",
)
# Test environment defaults are intentionally local to the test process.

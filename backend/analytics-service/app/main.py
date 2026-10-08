from fastapi import FastAPI, Response, status
from sqlalchemy.exc import SQLAlchemyError

from app.api.dashboard import router as dashboard_router
from app.database.connection import test_database_connection


app = FastAPI(
    title="LEAP Analytics Service",
    version="1.0.0",
)


@app.get("/health")
def health_check():
    return {
        "status": "healthy",
        "service": "analytics",
    }


@app.get("/ready")
def readiness_check(response: Response):
    try:
        database_ready = test_database_connection()
    except SQLAlchemyError:
        database_ready = False

    if not database_ready:
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE

        return {
            "status": "not_ready",
            "service": "analytics",
            "database": "unavailable",
        }

    return {
        "status": "ready",
        "service": "analytics",
        "database": "connected",
    }


app.include_router(dashboard_router)
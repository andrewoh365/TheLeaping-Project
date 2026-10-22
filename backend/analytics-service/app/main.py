from fastapi import FastAPI, Response, status
from sqlalchemy.exc import SQLAlchemyError

from app.api.dashboard import router as dashboard_router
from app.database.connection import get_reporting_status


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
        reporting = get_reporting_status()
    except SQLAlchemyError:
        reporting = None

    if reporting is None or not reporting["ready"]:
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE

        body = {
            "status": "not_ready",
            "service": "analytics",
            "database": "unavailable" if reporting is None else "connected",
        }

        if reporting is not None:
            body["reporting"] = reporting

        return body

    return {
        "status": "ready",
        "service": "analytics",
        "database": "connected",
        "reporting": reporting,
    }


app.include_router(dashboard_router)
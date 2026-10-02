from fastapi import FastAPI

from app.api.dashboard import router as dashboard_router


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


app.include_router(dashboard_router)
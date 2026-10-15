# Python analytics service (FastAPI served by uvicorn)
FROM python:3.9.25-slim

# Don't write .pyc files, and send logs straight to the container output
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

# Dependencies first so this layer is cached until requirements.txt changes
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Run as an unprivileged user instead of root (as it was previously)
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

COPY app ./app

USER appuser
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]

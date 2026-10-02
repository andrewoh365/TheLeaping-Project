# Analytics Feature – Setup & Run Guide

Use this guide to test the `feature/analytics` branch.

## 1. Get the branch

From the repo root:

```bash
cd ~/TheLeaping-Project

git fetch origin
git switch feature/analytics
git pull origin feature/analytics
```

---

## 2. One-time environment setup

Create a `.env` file in the root of the repo:

```text
TheLeaping-Project/.env
```

It should contain:

```env
DB_USERNAME=YOUR_DB_USERNAME
DB_PASSWORD=YOUR_DB_PASSWORD
JWT_SECRET=YOUR_JWT_SECRET
```

Use the same database username/password and JWT secret already used for the project.

**Do not commit `.env`.**

If you already use `application-local.yml` / `application-local.yaml` for Spring, that is fine. The important thing is that Spring can resolve the database credentials and JWT secret. The root `.env` is also used to pass the DB credentials into the Python analytics container.

---

# Terminal 1 — PostgreSQL + Spring Boot

From the repo root:

```bash
cd ~/TheLeaping-Project
```

Start the existing Postgres container:

```bash
docker start leap-postgres
docker exec leap-postgres pg_isready
```

Expected:

```text
accepting connections
```

If Jenkins is using port `8080`, stop it:

```bash
sudo systemctl stop jenkins
```

Load the environment variables:

```bash
set -a
source .env
set +a
```

Start Spring Boot:

```bash
cd backend/portfolio-app

bash ./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=local
```

Leave this terminal running.

Spring should run on:

```text
http://127.0.0.1:8080
```

Flyway should automatically apply any missing migrations, including the analytics/demo-data migrations.

---

# Terminal 2 — Python Analytics Service

## First time only: build the Docker image

```bash
cd ~/TheLeaping-Project/backend/analytics-service

docker build -t leap-analytics .
```

Then return to the repo root and load the environment variables:

```bash
cd ~/TheLeaping-Project

set -a
source .env
set +a
```

Run the analytics service:

```bash
docker run --rm \
  --name leap-analytics-service \
  --network host \
  -e DB_USERNAME \
  -e DB_PASSWORD \
  leap-analytics
```

Leave this terminal running.

The analytics service should run on:

```text
http://127.0.0.1:8000
```

Health check:

```bash
curl http://127.0.0.1:8000/health
```

Expected:

```json
{"status":"healthy","service":"analytics"}
```

### If Docker says `leap-analytics-service` already exists

Check whether it is already running:

```bash
docker ps --filter name=leap-analytics-service
```

If it is already running, you do not need to start another one.

If it is stale and needs to be removed:

```bash
docker rm -f leap-analytics-service
```

Then run the `docker run` command again.

---

# Terminal 3 — Angular Frontend

Go to the frontend:

```bash
cd ~/TheLeaping-Project/frontend
```

First time after switching to this branch:

```bash
npm ci
```

Start Angular:

```bash
npm start -- --host 0.0.0.0 --port 4200
```

Leave this terminal running.

The proxy is already configured in:

```text
frontend/proxy.conf.json
frontend/angular.json
```

Frontend requests to:

```text
/api/...
```

are proxied to Spring on port `8080`.

---

# Open the Frontend in VS Code

If you are connected to the EC2 machine through VS Code Remote SSH:

1. Press `Ctrl + Shift + P`
2. Search for `Simple Browser: Show`
3. Open:

```text
http://localhost:4200
```

---

# Demo Internal Accounts

## Admin

```text
Email: analytics.admin@leap.local
Password: Admin123!
```

## Analyst

```text
Email: analytics.analyst@leap.local
Password: Analyst123!
```

Both should be able to log in and reach:

```text
/analytics
```

A normal customer should continue using:

```text
/dashboard
```

---

# Optional — Python venv for running analytics tests

The venv is **not required to run the application** because the analytics service runs in Docker.

Only use this if you want to run the Python tests directly:

```bash
cd ~/TheLeaping-Project/backend/analytics-service

python3 -m venv .venv
source .venv/bin/activate

pip install -r requirements.txt

pytest
```

When finished:

```bash
deactivate
```

`.venv`, Python cache files, and pytest cache files are ignored by Git.

---

# Quick Health Check

The full stack should be:

```text
PostgreSQL          5432
Spring Boot         8080
Python Analytics    8000
Angular Frontend    4200
```

Check Postgres:

```bash
docker exec leap-postgres pg_isready
```

Check analytics:

```bash
curl http://127.0.0.1:8000/health
```

Check Angular:

```bash
curl -I http://127.0.0.1:4200
```

A `403` response from the Spring root URL itself is not necessarily a problem because Spring Security protects backend routes.

---

# Common Issues

### Port 8080 opens Jenkins instead of Spring

Stop Jenkins:

```bash
sudo systemctl stop jenkins
```

Then restart Spring Boot.

### Analytics container name already exists

```bash
docker ps --filter name=leap-analytics-service
```

If needed:

```bash
docker rm -f leap-analytics-service
```

Then start the analytics container again.

### Frontend login works but analytics returns `403`

Make sure the frontend was started from this branch and that `frontend/src/main.ts` still registers the JWT interceptor through `withInterceptorsFromDi()`.

### Frontend page does not open in a normal browser

If using VS Code Remote SSH, use `Simple Browser: Show` and open:

```text
http://localhost:4200
```

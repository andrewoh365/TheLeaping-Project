# Leaping Portfolio

A full-stack online trading platform application for portfolio management and market analysis.

## 🎯 Features

### ✅ Authentication & Authorization (Completed)
- **JWT-based Authentication**: Secure token generation and validation
- **User Registration**: New user account creation with validation
- **User Login**: Email/password authentication with error handling
- **Session Management**: Automatic token refresh and expiration handling
- **Protected Routes**: Dashboard and authenticated endpoints secured with route guards
- **Material UI Design**: Professional, responsive UI components

### 📱 Frontend (Angular)
- Modern single-page application built with Angular 17
- Material-UI components for consistent design
- Form validation and error handling
- Reactive forms with real-time validation
- JWT token interceptor for automatic authentication header injection
- Route guards for protected pages

### 🔧 Backend (Spring Boot)
- RESTful API with JWT token support
- User authentication endpoints (login, register, refresh, me)
- Spring Security integration
- Database schema with Flyway migrations
- CORS configuration for development and production

### Analytics Reporting and Filter Semantics
- Filters are composable: dates, asset type, market, current order status,
	client segment, and the unique `instrument_id` constrain the applicable
	activity metrics. Instrument selection never uses symbol alone.
- Dates are inclusive UTC calendar dates. The effective period is returned by
	the API, including the default last 90 UTC calendar days when dates are
	omitted.
- Order counts and client activity use `trade_orders.submitted_at`. Trading
	volume uses `trades.executed_at`, so submitted-order counts and executed
	trade totals can legitimately represent different event populations.
- `order_status` means the order's current stored status, not an as-of
	historical status. Rejection reasons are only applicable to rejected
	orders.
- Client segments are classified from each client's complete 90-day order
	window ending on the report end date. Activity filters narrow segment
	metrics, but do not change classification or `total_clients`.
- Verify KPI and chart relationships with the isolated PostgreSQL analytics
	tests: outcome counts reconcile to Total Orders, volume buckets reconcile
	to Trading Volume, and segment activity reconciles under equivalent filters.
- Run `./scripts/test.sh` for the standard Spring, analytics, and Angular
	checks. The command creates a disposable PostgreSQL database and runs the
	analytics SQL integration tests as part of the standard analytics suite.
- Top-five instrument charts intentionally exclude instruments outside the
	top five; distinct-client counts across time buckets can also repeat a
	customer and should not be summed as a global distinct-client total.
- The protected dashboard remains available only to ADMIN and ANALYST users.

### Reporting Isolation
Spring Boot and Flyway use the primary PostgreSQL database. Analytics follows
this one-way request path:

```text
Angular -> Spring Boot -> FastAPI -> PostgreSQL reporting standby
                                      ^
                         asynchronous physical streaming replication
                                      ^
                         PostgreSQL primary used for trading and writes
```

The Spring datasource and Flyway datasource remain pointed at the primary. The
FastAPI service requires `ANALYTICS_DB_HOST`, `ANALYTICS_DB_PORT`,
`ANALYTICS_DB_NAME`, `ANALYTICS_DB_USERNAME`, and `ANALYTICS_DB_PASSWORD`; it
does not fall back to `DB_*` credentials or the primary host. FastAPI runs with
a bounded pool, read-only transactions, and a statement timeout. There is no
custom table-copy or duplicate trading-data synchronization process: the
standby is maintained by PostgreSQL physical streaming replication.

The primary creates the `leap_replication` role, the `leap_reporting` reader,
and the physical slot `leap_reporting_slot`. The replication role has only
`LOGIN` and `REPLICATION` privileges. The reporting role has `CONNECT`, schema
`USAGE`, SELECT only on `portfolios`, `trade_orders`, `trades`, `instruments`,
and `markets`, and `default_transaction_read_only` enabled; it has no create,
superuser, or replication privileges. Provisioning removes old direct,
inherited, public, and default table privileges, and does not grant SELECT on
future application tables. The role remains a member of `pg_read_all_stats`
so the reporting readiness check can inspect standby receiver and replay
statistics. This is an explicit accepted security boundary:
`pg_read_all_stats` exposes broader PostgreSQL statistics than the five
reporting tables, but does not grant application-table SELECT, write, create,
superuser, or replication privileges. A dedicated monitoring account is the
follow-up design if those statistics must be removed from the reporting
account.

Replication is asynchronous. Analytics can be slightly behind live trading.
The slot has `max_slot_wal_keep_size=2048MB`; if the standby remains offline
long enough to exceed retained WAL, it must be rebuilt rather than allowed to
silently diverge. A reporting outage returns HTTP 503 through Spring and the
Angular dashboard shows an unavailable message with a retry action. No
primary fallback is permitted, so trading continues independently while
reporting is unavailable.

#### Local or EC2 startup

The local lifecycle scripts also apply to an EC2 host running Docker:

```bash
./scripts/setup.sh
./scripts/start.sh
./scripts/status.sh
```

`setup.sh` creates missing ignored values for `REPLICATION_PASSWORD`,
`REPORTING_DB_PASSWORD`, and `ANALYTICS_INTERNAL_TOKEN`, configures the
primary HBA, and builds the reporting image. `start.sh` starts the preserved
primary, runs Spring Boot/Flyway, then provisions the reporting roles and slot
before starting the standby and waiting for recovery. FastAPI then runs against
the `reporting-postgres` network alias. The local primary database and FastAPI
are published only on loopback
(`127.0.0.1:5432` and `127.0.0.1:8000`); neither is exposed to the LAN. Spring
sends the internal token in
`X-Analytics-Internal-Token`; Angular never receives that token.
An existing primary container created before the loopback hardening may retain
an older `0.0.0.0:5432` binding; inspect it with `docker port leap-postgres` and
recreate only the container, retaining `leap-postgres-data`, during an approved
maintenance window.

The standby's replication `.pgpass` is created with mode `0600` under the
container's ephemeral `/run/postgresql` filesystem and is recreated on every
standby start. It is not stored in `postgres-replica-data`, so stopping the
container removes the runtime credential from the reporting volume and its
backups. Restart the standby after rotating `REPLICATION_PASSWORD`; startup
rewrites the passfile and the standby connection configuration. The startup
script also removes the legacy persistent `/var/lib/postgresql/data/.pgpass`
left by older images.

#### Docker Compose startup

Compose uses `postgres` as the primary and `postgres-replica` as the private
standby on `leap-data-network`. The only published application port is the
frontend port. The replica, FastAPI, and primary PostgreSQL ports are not
published to the host:

```bash
docker compose up --build
```

The Compose `postgres-replication-init` service provisions the primary before
the replica performs its initial `pg_basebackup`. The primary volume and
`postgres-replica-data` are separate. Set the following in ignored `.env`
values, using unique local secrets and never committing them:

```text
DB_USERNAME
DB_PASSWORD
JWT_SECRET
REPLICATION_PASSWORD
REPORTING_DB_PASSWORD
ANALYTICS_INTERNAL_TOKEN
```

#### Health, lag, and recovery

`/health` is a liveness endpoint. FastAPI `/ready` is reporting-specific and
returns 503 unless the database is in recovery, the WAL receiver is streaming,
and replay is within `ANALYTICS_MAX_REPLAY_LAG_BYTES` (default 16777216, one
WAL segment) of the primary's WAL end as last reported to the receiver
(`latest_end_lsn`). This covers WAL not yet received as well as WAL received
but not replayed. The standby cannot see the primary's exact current
position; check `pg_stat_replication` on the primary for that. It reports
receiver state, received/replayed LSNs, replay backlog, lag in bytes, and
replay timestamp, without returning credentials or tokens. Inspect the primary and standby with:

```sql
SELECT pg_is_in_recovery();
SELECT * FROM pg_stat_replication;
SELECT * FROM pg_stat_wal_receiver;
```

Use `./scripts/status.sh` for the local container state and replication lag.
Normal `./scripts/stop.sh` and `./scripts/start.sh` preserve both volumes.
If the standby has a partial cluster or has exceeded the WAL retention window,
stop the services, remove only the reporting volume after operator review, and
rebuild it from the primary:

```bash
./scripts/stop.sh
docker volume rm leap-reporting-postgres-data
./scripts/start.sh
```

The lifecycle scripts refuse to delete or reinitialize an invalid reporting
volume automatically. `./scripts/reset.sh` preserves both databases by default.
`./scripts/reset.sh --database` is explicitly destructive: after confirmation
it deletes both the primary and reporting volumes, then reruns setup and
Flyway. It must never be used as an automatic replication recovery action.

#### Verification

Run the standard checks with:

```bash
./scripts/test.sh
```

This runs the Spring suite against a disposable PostgreSQL database, the
analytics suite, and an Angular production build. Focused checks can be run
with `pytest` in `backend/analytics-service`, the focused analytics Maven test
classes in `backend/portfolio-app`, and `npm run build` in `frontend`.
The isolated performance benchmark is checked in under
`benchmarks/analytics-isolation/`. It records a no-analytics trading baseline
and concurrent analytics p50/p95 latency, throughput, errors, connection
usage, container CPU/memory, and WAL/replication state without using the
project primary. Its safety gate blocked execution on the current 2-vCPU host
because only about 2.2 GiB was available while the development stack was
running; no performance result is claimed until it runs on a host with at
least 3 GiB available memory.

## 🚀 Quick Start

There are two ways to run the whole application locally. Both start Spring Boot, the analytics
service, the PostgreSQL primary and its reporting replica, and the Angular frontend.

### Option 1: Docker Compose

**Needs:** Docker with Docker Compose.

```bash
cp .env.example .env
# Edit .env and give every value your own local secret (see "Docker Compose startup" above)
docker compose up -d --build      # older installations: docker-compose up -d --build
```

The first start takes a few minutes: the images are built, Flyway creates the database, and the
replica is cloned from the primary. Stop everything with `docker compose down`; the databases are
kept.

### Option 2: Local scripts

**Needs:** Docker, Git, Java 21, Node.js 20.20.2 (see `.nvmrc`), npm and curl.

```bash
./scripts/setup.sh     # once, after cloning
./scripts/start.sh
./scripts/status.sh
./scripts/stop.sh
```

See "Local or EC2 startup" above for what the scripts do.

### Check that Spring Boot is healthy

Spring Boot exposes `/actuator/health`. Jenkins uses it, and AWS will too. It reports `UP` once
the database is reachable and the startup price backfill has finished, about a minute after
start. A `503` before that means "still starting".

```bash
# Docker Compose
docker compose exec portfolio-app wget -qO- http://localhost:8080/actuator/health

# Local scripts (Spring Boot runs directly on port 8080)
curl http://localhost:8080/actuator/health
```

Expected: `{"groups":["liveness","readiness"],"status":"UP"}`. Do not test it at
`http://localhost:4200/actuator/health`: nginx only forwards `/api/` to Spring Boot, so that
address shows the Angular app instead. No other actuator endpoint is public.

### Access Application
- **Registration**: http://localhost:4200/register
- **Login**: http://localhost:4200/login
- **Dashboard**: http://localhost:4200/dashboard

## 📁 Project Structure

```
TheLeaping-Project/
├── backend/
│   ├── portfolio-app/              # Spring Boot API (Java 21)
│   │   ├── src/main/java/com/leaping/portfolio_app/
│   │   │                           # One package per feature: admin, analytics, audit, auth,
│   │   │                           # config, holdings, instrument, market, portfolio, trade,
│   │   │                           # watchlist
│   │   └── src/main/resources/
│   │       ├── application.yaml
│   │       └── db/migration/       # Flyway migrations
│   ├── analytics-service/          # FastAPI reporting service, reads the replica
│   └── api-tests.http              # Example API requests
├── frontend/                       # Angular app, served by nginx in Docker
├── infrastructure/
│   ├── postgres/                   # Local PostgreSQL primary and reporting replica
│   └── README.md                   # AWS deployment blueprint
├── benchmarks/analytics-isolation/ # Analytics isolation benchmark
├── scripts/                        # setup, start, status, stop, reset, test
├── src/data/                       # Older SQL files, not used by the application
├── docker-compose.yaml             # Runs the whole stack in containers
├── Jenkinsfile                     # CI pipeline
├── .env.example                    # Template for the ignored .env file
├── ARCHITECTURE.md
├── BUY_SELL_IMPLEMENTATION_PLAN.md
└── README.md                       # This file
```

## 📚 Documentation

- **[Architecture](./ARCHITECTURE.md)** - System architecture
- **[Buy/Sell Implementation Plan](./BUY_SELL_IMPLEMENTATION_PLAN.md)** - Trading feature design
- **[AWS Deployment Blueprint](./infrastructure/README.md)** - How the application will run on AWS
- **[Analytics Isolation Benchmark](./benchmarks/analytics-isolation/README.md)** - Reporting performance benchmark

## 🔐 Security Features

- ✅ Password encryption with bcrypt
- ✅ JWT token-based authentication
- ✅ Automatic token refresh mechanism
- ✅ Route guards for protected pages
- ✅ CORS configuration
- ✅ HTTP interceptor for token injection
- ✅ Session expiration handling

## 🧪 Acceptance Criteria (All Met ✅)

- ✅ User can authenticate using valid credentials
- ✅ Invalid credentials display an error message
- ✅ Successful authentication redirects user to dashboard
- ✅ Session or JWT token is generated
- ✅ User remains authenticated until logout or session expiration

## 🔄 Branching Strategy

```text
feature/* or fix/*  ──PR──►  dev  ──PR when ready to release──►  main  ──►  AWS (planned)
```

- **Feature and fix branches** are created from `dev` and merged back into `dev` through a
  reviewed pull request.
- **`dev`** is where finished work comes together and is tested.
- **`main`** only changes through a pull request from `dev`. It is the branch that gets deployed.

Database migrations are shared by every branch: never edit, rename or renumber a migration that
has already been merged. Add a new one instead.

## 👥 Team

- Sarah Berhe
- Satvik Movva
- Maanya Naveen
- Andrew Oh
- Alveena Rehman
- Miguel Trejo Cruz

## 📖 Technologies Used

### Frontend
- Angular 17
- Angular Material
- RxJS
- TypeScript
- SCSS

### Backend
- Spring Boot 4.1.1
- Spring Security
- Spring Data JPA
- JWT (JSON Web Tokens)
- Flyway
- Spring Boot Actuator (health check only)

### Analytics
- Python, FastAPI
- SQLAlchemy, psycopg

### Data and Infrastructure
- PostgreSQL 16, with a streaming replica for reporting
- Docker and Docker Compose, nginx
- Jenkins
- Planned: AWS (ECS on Fargate, RDS, Application Load Balancer, ECR)

## Continuous Integration (Jenkins)

Jenkins runs the [`Jenkinsfile`](./Jenkinsfile) for every branch and pull request in this
repository:

1. **Spring Boot tests** against a throwaway PostgreSQL database
2. **Build analytics image**
3. **Python tests** inside the analytics image
4. **Build Spring Boot image**
5. **Spring Boot health check**: starts the image just built against a throwaway PostgreSQL and
   waits up to 3 minutes for `/actuator/health` to report `UP`, the same check AWS ECS will use.
   Price updates run every second in this stage so that startup deadlocks show up every time.
6. **Build frontend image**, which also checks the Angular size budgets

Jenkins runs on the team's VM, which GitHub cannot reach, and builds are not scheduled. Start a
build from Jenkins with **Scan Repository Now**. Pull requests from forks are not built.

## Deployment

The application is not deployed yet. The AWS plan (services, settings, secrets, health checks,
order of setup) is in the **[AWS Deployment Blueprint](./infrastructure/README.md)**. Once the AWS
account is available, deploys will run from `main` through Jenkins, after a manual approval.

## Development Workflow

1. Create a feature branch from `dev`
2. Implement the feature and run `./scripts/test.sh`
3. Push the branch and open a pull request into `dev` with a detailed description
4. In Jenkins, click **Scan Repository Now** and wait for the build to pass
5. Code review and approval, then merge into `dev`
6. To release, open a pull request from `dev` into `main`

## 🤝 Contributing

Please refer to the team's development guidelines and ensure all code follows the project's coding standards.

## 📞 Support

For setup issues or questions:
- **Running locally**: see [Quick Start](#-quick-start) and [Reporting Isolation](#reporting-isolation)
- **Architecture**: see [ARCHITECTURE.md](./ARCHITECTURE.md)
- **Deployment**: see the [AWS Deployment Blueprint](./infrastructure/README.md)


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
	checks. To enable analytics PostgreSQL integration tests directly, set
	`ANALYTICS_TEST_DATABASE_URL` to a disposable isolated database.
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
future application tables. The role remains a member of
`pg_read_all_stats` for standby monitoring; that predefined role exposes
broader PostgreSQL statistics and may be separated into a dedicated monitoring
account later.

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
primary HBA, provisions roles and the slot, and builds the reporting image.
`start.sh` starts the preserved primary first, starts the standby, waits for
recovery, and then starts FastAPI against the `reporting-postgres` network
alias. The local primary database and FastAPI are published only on loopback
(`127.0.0.1:5432` and `127.0.0.1:8000`); neither is exposed to the LAN. Spring
sends the internal token in
`X-Analytics-Internal-Token`; Angular never receives that token.
An existing primary container created before the loopback hardening may retain
an older `0.0.0.0:5432` binding; inspect it with `docker port leap-postgres` and
recreate only the container, retaining `leap-postgres-data`, during an approved
maintenance window.

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
and the replay backlog (received but not yet replayed WAL) is at most
`ANALYTICS_MAX_REPLAY_LAG_BYTES` (default 16777216, one WAL segment). It
reports receiver state, received/replayed LSNs, replay backlog in bytes, and
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
classes in `backend/portfolio-app`, and `npm run build` in `frontend`. No
The isolated performance benchmark is checked in under
`benchmarks/analytics-isolation/`. It records a no-analytics trading baseline
and concurrent analytics p50/p95 latency, throughput, errors, connection
usage, container CPU/memory, and WAL/replication state without using the
project primary. Its safety gate blocked execution on the current 2-vCPU host
because only about 2.2 GiB was available while the development stack was
running; no performance result is claimed until it runs on a host with at
least 3 GiB available memory.

## 🚀 Quick Start

### Prerequisites
- **Backend**: Java 21, Maven 3.6+, PostgreSQL 16+
- **Frontend**: Node.js v18+, npm or yarn

### Backend Setup (Local Development)
```bash
cd backend/portfolio-app

# 1. Copy and configure local secrets
cp src/main/resources/application-local.yaml.example src/main/resources/application-local.yaml
# Edit application-local.yaml with your DB password and JWT secret

# 2. Build and run
mvn clean compile
mvn spring-boot:run
# Backend runs on http://localhost:8080
```

**What You Need:**
- PostgreSQL running with `leaping_db` database
- `application-local.yaml` with your DB password (auto .gitignored, never committed)
- Java 21+, Maven 3.9+

### Frontend Setup
```bash
cd frontend
npm install
npm start
# Frontend runs on http://localhost:4200
```

### Access Application
- **Registration**: http://localhost:4200/register
- **Login**: http://localhost:4200/login
- **Dashboard**: http://localhost:4200/dashboard

## 📁 Project Structure

```
The Leaping Project/
├── backend/
│   └── portfolio-app/       # Spring Boot application
│       ├── src/
│       │   ├── main/java/com/leaping/portfolio_app/
│       │   │   ├── controller/    # REST API endpoints
│       │   │   ├── service/       # Business logic
│       │   │   ├── entity/        # Database entities
│       │   │   ├── repository/    # Data access layer
│       │   │   ├── security/      # JWT token handling
│       │   │   ├── dto/           # Request/response DTOs
│       │   │   └── config/        # Spring configuration
│       │   └── resources/
│       │       ├── application.yaml
│       │       └── db/migration/  # Flyway migrations
│       ├── pom.xml
│       └── BACKEND_AUTH_GUIDE.md
├── frontend/                # Angular application
│   ├── src/
│   │   ├── app/
│   │   │   ├── components/    # Login, Register, Dashboard
│   │   │   ├── services/      # Authentication service
│   │   │   ├── interceptors/  # JWT interceptor
│   │   │   ├── guards/        # Route guards
│   │   │   ├── app.routes.ts  # Routing config
│   │   │   └── app.component.ts
│   │   ├── index.html
│   │   ├── main.ts
│   │   └── styles.scss
│   ├── package.json
│   ├── angular.json
│   ├── tsconfig.json
│   └── FRONTEND_SETUP.md
├── database/                # Database configuration
├── docker/                  # Docker configuration
├── infrastructure/          # Infrastructure setup
├── AUTHENTICATION_SETUP.md  # Complete integration guide
├── IMPLEMENTATION_SUMMARY.md# Feature implementation summary
├── ENVIRONMENT_CONFIG.md    # Environment and Docker config
├── Jenkinsfile             # CI/CD pipeline definition
└── README.md               # This file
```

## 📚 Documentation

- **[Authentication Setup Guide](./AUTHENTICATION_SETUP.md)** - Complete end-to-end setup and integration guide
- **[Implementation Summary](./IMPLEMENTATION_SUMMARY.md)** - Overview of completed features
- **[Frontend Setup Guide](./frontend/FRONTEND_SETUP.md)** - Angular frontend configuration and setup
- **[Backend Authentication Guide](./backend/portfolio-app/BACKEND_AUTH_GUIDE.md)** - Spring Boot backend implementation
- **[Environment Configuration](./ENVIRONMENT_CONFIG.md)** - Configuration for different environments

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

### Trunk-Based Development

Trunk-based development works well for a short 12-week project because everyone integrates into one main branch frequently, which reduces merge conflicts and keeps progress visible in real time. It also helps to deliver working features continuously instead of spending time managing long-lived branches.

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
- PostgreSQL 16

## 🚢 Deployment

See [Environment Configuration](./ENVIRONMENT_CONFIG.md) for Docker and production deployment setup.

## 📝 Development Workflow

1. Create a feature branch from `main`
2. Implement the feature
3. Create a pull request with detailed description
4. Code review and approval
5. Merge to `main` using rebase strategy
6. Automatically deploy via Jenkins pipeline

## 🤝 Contributing

Please refer to the team's development guidelines and ensure all code follows the project's coding standards.

## 📞 Support

For setup issues or questions:
- **Frontend**: See `frontend/FRONTEND_SETUP.md`
- **Backend**: See `backend/portfolio-app/BACKEND_AUTH_GUIDE.md`
- **Integration**: See `AUTHENTICATION_SETUP.md`


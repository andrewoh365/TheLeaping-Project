# LEAP on AWS: deployment blueprint

This is the plan for running this project on AWS. Nothing here exists in AWS yet: we are still waiting
for our AWS account. Values in `<angle brackets>` get filled in once it arrives.

Everything below was checked against the code, and the container sizes and startup times were
measured, so creating the AWS resources should mostly be copying from this page.

The `postgres/` folder next to this file is the **local** Docker Compose primary and replica
setup. On AWS, RDS replaces it (see [Database](#database-rds)).

## Architecture

```
Browser ──► Application Load Balancer (port 80, later 443)
                 │
                 ▼
            frontend (nginx) ──/api/──► portfolio-app (Spring) ──► analytics-service (FastAPI)
                                              │                          │
                                              ▼                          ▼
                                     RDS PostgreSQL primary ──► RDS read replica
                                     (trading, all writes)      (reporting, read-only)
```

- Everything runs in one **ECS cluster** (`leap`) on **Fargate** (Linux, x86_64).
- Only the **frontend** is behind the load balancer. nginx forwards `/api/` to Spring, exactly as in
  `docker-compose.yaml`.
- The containers find each other through **ECS Service Connect** (namespace `leap`) using the
  **same names and ports as docker-compose**: `portfolio-app:8080` and `analytics-service:8000`.
  This keeps `frontend/nginx.conf` and the local setup unchanged.
- `postgres` from docker-compose becomes an **RDS primary**, and `postgres-replica` becomes an
  **RDS read replica**. Analytics only ever reads from the replica, as it does locally.

## The three services

| | frontend | portfolio-app | analytics-service |
|---|---|---|---|
| Built from | `frontend/Dockerfile` | `backend/portfolio-app/Dockerfile` | `backend/analytics-service/Dockerfile` |
| ECR repository | `leap-frontend` | `leap-portfolio-app` | `leap-analytics` |
| Port | 80 | 8080 | 8000 |
| Service Connect name | client only | `portfolio-app` | `analytics-service` |
| Size (CPU / memory) | 0.25 vCPU / 0.5 GB | **1 vCPU / 2 GB** | 0.25 vCPU / 0.5 GB |
| Number of copies | 1 (can be more) | **exactly 1** | 1 (can be more) |
| Reached by | load balancer | frontend | portfolio-app |
| Database | none | RDS primary | RDS read replica |
| Needs the internet | no | yes (news and movers feeds) | no |

ECR repository names match the image names the `Jenkinsfile` already builds.

### frontend (nginx + Angular)

- **Settings:** none.
- **Health check:** done by the load balancer's target group: path `/`, success code `200`.
- **Start order:** create the `portfolio-app` service first. nginx refuses to start if the name
  `portfolio-app` does not exist yet.

### portfolio-app (Spring Boot)

**Settings** (ECS task definition, `environment` and `secrets`):

| Variable | Value | Kind |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<rds-endpoint>:5432/leaping_db` | plain |
| `DB_USERNAME` | from the RDS secret, key `username` | secret |
| `DB_PASSWORD` | from the RDS secret, key `password` | secret |
| `JWT_SECRET` | `leap/jwt-secret` (see [Secrets](#secrets)) | secret |
| `ANALYTICS_SERVICE_BASE_URL` | `http://analytics-service:8000` | plain |
| `ANALYTICS_INTERNAL_TOKEN` | `leap/analytics-internal-token` (same value as analytics-service) | secret |
| `CORS_ORIGINS` | `http://<load-balancer-dns-name>` (later `https://<domain>`) | plain |
| `SPRING_PROFILES_ACTIVE` | `prod` | plain |
| `TRADING_MARKETOVERRIDE_ALLOWED` | `true` only if we want the after-hours trading toggle for demos | optional |

`SPRING_PROFILES_ACTIVE=prod` just replaces the `local` default from `application.yaml`; there is
no `application-prod.yaml`. Without `JWT_SECRET` Spring refuses to start, which is intended.
Without `ANALYTICS_INTERNAL_TOKEN` Spring still starts, but every analytics request fails. Spring
sends it to analytics in the `X-Analytics-Internal-Token` header; the browser never sees it.

**Health check** (container health check in the task definition):

```json
"healthCheck": {
  "command": ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health > /dev/null || exit 1"],
  "interval": 15,
  "timeout": 5,
  "retries": 3,
  "startPeriod": 300
}
```

`/actuator/health` only says `UP` after the database is reachable **and** the startup price
backfill has finished. Measured with a fresh database: 88 s at 1 vCPU, 148 s at 0.5 vCPU (too
close to the limit, hence 1 vCPU). Restarts with existing data took 42 s. `startPeriod` is set to
300 s, the maximum ECS allows, because RDS over the network is slower than a local database.

**Why exactly 1 copy:** the price simulation is protected by a lock that only works inside one
Java process. Two copies would both write prices. Keep autoscaling **off** for this service
(ShedLock would be the fix if we ever need more copies). During a deploy ECS briefly runs the old
and the new copy together; that overlap is short and at worst logs a duplicate price row error.

**On first start** Flyway creates all tables in RDS (migrations V1 onwards), then the price
history is backfilled. Expect about a minute and a half before it reports healthy.

**Internet access** is needed for `news.google.com` and `api.nasdaq.com`. If either is blocked
from AWS, the dashboard shows those panels empty and everything else keeps working.

### analytics-service (FastAPI)

**Settings:**

| Variable | Value | Kind |
|---|---|---|
| `ANALYTICS_DB_HOST` | `<rds-read-replica-endpoint>` (never the primary) | plain |
| `ANALYTICS_DB_PORT` | `5432` | plain |
| `ANALYTICS_DB_NAME` | `leaping_db` | plain |
| `ANALYTICS_DB_USERNAME` | `leap_reporting` | plain |
| `ANALYTICS_DB_PASSWORD` | `leap/reporting-db-password` | secret |
| `ANALYTICS_INTERNAL_TOKEN` | `leap/analytics-internal-token` (same value as portfolio-app) | secret |

Optional tuning, defaults are fine: `ANALYTICS_DB_POOL_SIZE` (2), `ANALYTICS_DB_MAX_OVERFLOW` (0),
`ANALYTICS_DB_POOL_TIMEOUT_SECONDS` (10), `ANALYTICS_DB_CONNECT_TIMEOUT_SECONDS` (5),
`ANALYTICS_DB_STATEMENT_TIMEOUT_MS` (30000) and `ANALYTICS_MAX_REPLAY_LAG_BYTES` (16 MiB).

The analytics service has no fallback to the primary or to the `DB_*` variables: it refuses to
run without the `ANALYTICS_DB_*` settings.

**Health check:** ECS checks `/health` (is the process alive?), not `/ready`. `/ready` only passes
while the database is a replica that is streaming and less than 16 MiB behind. When the replica
lags or is down, restarting the analytics container would not fix anything, and ECS would keep
restarting it. Spring already answers 503 for the dashboard and the frontend shows "unavailable"
with a retry button. The image has neither `curl` nor `wget`, so the check uses Python itself.

```json
"healthCheck": {
  "command": ["CMD", "python", "-c", "import urllib.request; urllib.request.urlopen('http://localhost:8000/health', timeout=4)"],
  "interval": 15,
  "timeout": 5,
  "retries": 3,
  "startPeriod": 30
}
```

Stateless, so it is the safe service to try autoscaling on (for example 1 to 3 copies on CPU).
Each copy opens at most 2 database connections.

## Database (RDS)

Two RDS instances: a **primary** for Spring and Flyway, and a **read replica** for analytics.

| Setting | Primary | Read replica |
|---|---|---|
| Engine | PostgreSQL **16** (same as docker-compose) | copied from the primary |
| How to create | create database | primary → Actions → **Create read replica** |
| Initial database name | `leaping_db` | copied from the primary |
| Instance | `db.t4g.micro`, single AZ | `db.t4g.micro` (about $14/month more) |
| Storage | 20 GB gp3 | copied from the primary |
| Credentials | "Manage master credentials in AWS Secrets Manager" | same users as the primary |
| Public access | **No** | **No** |
| Automated backups | 7 days (required for a read replica) | not needed |
| Security group | `leap-rds` | `leap-rds-replica` |

- RDS keeps the replica in sync by itself. We do **not** create the `leap_replication` user, the
  `leap_reporting_slot` replication slot or any `pg_hba` rules that the local setup needs.
- RDS requires encrypted connections by default. Both database drivers (Java and Python) handle
  that automatically, so no extra settings are needed.
- Migration V7 creates the `pgcrypto` extension. RDS supports it and the master user may create it.
- **Never edit, rename or renumber a migration that has already run.** Add a new one instead.
  A renumbered migration stops Spring from starting, and unlike our local databases RDS cannot just
  be wiped.

**The `leap_reporting` user.** Analytics logs in as `leap_reporting`, a read-only user. It is
created on the **primary** (the replica is read-only) and RDS copies it to the replica. Create it
once, **after** Spring's first start, because its permissions are granted on tables Flyway
creates. It needs exactly what `postgres/provision-primary.sh` gives it locally: login, `CONNECT`
on `leaping_db`, `USAGE` on schema `public`, `SELECT` only on `portfolios`, `trade_orders`,
`trades`, `instruments` and `markets`, `default_transaction_read_only = on`, and membership in
`pg_read_all_stats` for the readiness check.

**Do not run `postgres/provision-primary.sh` against RDS as it is.** It also creates a replication
user and a physical replication slot. RDS manages replication itself, and a slot nothing reads
from makes the primary keep its WAL forever until the disk fills up. Use only the
`leap_reporting` part. The primary is not public, so run it from inside the VPC, for example as a
one-off ECS task with the `postgres:16` image and the `leap-portfolio-app` security group.

**To verify on the first deployment:** that `/ready` reports ready on the RDS read replica (it
reads `pg_stat_wal_receiver`), and that the RDS master user may grant `pg_read_all_stats`.

## Secrets

Stored in **AWS Secrets Manager** and injected by ECS as environment variables. Never in the
repository, which is public.

| Secret | Used by | How to create |
|---|---|---|
| RDS master secret (created by RDS) | portfolio-app, creating `leap_reporting` | automatically when the primary is created |
| `leap/jwt-secret` | portfolio-app | new value from `openssl rand -base64 64` |
| `leap/analytics-internal-token` | portfolio-app **and** analytics-service | new value from `openssl rand -hex 32` |
| `leap/reporting-db-password` | analytics-service, creating `leap_reporting` | new value from `openssl rand -hex 24` |

`JWT_SECRET` must be at least 64 characters because tokens are signed with HS512. Do not reuse
the development or test values from `.env.example`, the `Jenkinsfile` or old commits.

## Networking

- One VPC with subnets in **two availability zones** (the load balancer requires two).
- **Open decision:** containers in private subnets with a NAT Gateway (standard, about $33/month
  more), or in public subnets with public IPs, locked down by the security groups below.

**Security groups** (all outbound traffic allowed):

| Security group | Allows in |
|---|---|
| `leap-alb` | port 80 (later 443) from anywhere |
| `leap-frontend` | port 80 from `leap-alb` |
| `leap-portfolio-app` | port 8080 from `leap-frontend` |
| `leap-analytics` | port 8000 from `leap-portfolio-app` |
| `leap-rds` | port 5432 from `leap-portfolio-app` |
| `leap-rds-replica` | port 5432 from `leap-analytics` |

**Load balancer:** Application Load Balancer, listener HTTP 80 forwarding to a target group for
the frontend (target type `ip`, port 80, health check path `/`). Later: an HTTPS 443 listener with
a free ACM certificate, port 80 redirecting to 443, and `CORS_ORIGINS` changed to the `https://`
address.

## Container images (ECR)

- Three repositories: `leap-frontend`, `leap-portfolio-app`, `leap-analytics`.
- Images are tagged with the Jenkins `IMAGE_TAG` (for example `main-42`), never `latest`, so we
  always know which build is running and can roll back to an earlier tag.
- Lifecycle policy: keep the last 10 images per repository.

## Logs (CloudWatch)

All three services already log to standard output and never to files, so ECS can forward
everything. Each container definition gets:

```json
"logConfiguration": {
  "logDriver": "awslogs",
  "options": {
    "awslogs-group": "/ecs/leap/<service-name>",
    "awslogs-region": "<region>",
    "awslogs-stream-prefix": "<service-name>",
    "awslogs-create-group": "true"
  }
}
```

Set log retention to 14 days to keep costs down. Keep logging to standard output: logs written to
a file inside a container never reach CloudWatch and disappear when ECS replaces the container.

## Permissions (IAM)

| Role | Purpose | Permissions |
|---|---|---|
| Task execution role | lets ECS pull images, write logs and read our secrets | `AmazonECSTaskExecutionRolePolicy` + `secretsmanager:GetSecretValue` on the four secrets |
| Task role | lets our code call AWS | not needed: none of our code calls AWS |
| Jenkins deploy user | lets Jenkins push images and deploy | ECR push (`AmazonEC2ContainerRegistryPowerUser`), `ecs:RegisterTaskDefinition`, `ecs:DescribeTaskDefinition`, `ecs:UpdateService`, `ecs:DescribeServices`, `iam:PassRole` on the task execution role |

If our account turns out to be an AWS Academy Learner Lab, we cannot create roles or users: use
the provided `LabRole` for the ECS roles, and the lab's temporary keys for Jenkins.

Jenkins stores the deploy keys in **Manage Jenkins → Credentials** and only uses them in deploy
stages that run on `main` after someone approves. Never in the `Jenkinsfile` or the repository.

## First deployment, in order

1. **Billing alert** (AWS Budgets) before anything else.
2. VPC, subnets and security groups.
3. RDS primary. When it is *Available*, create its read replica (that takes a while, so start it
   early).
4. The three secrets: `leap/jwt-secret`, `leap/analytics-internal-token`,
   `leap/reporting-db-password`.
5. ECR repositories, then push the three images by hand from the Jenkins VM.
6. Load balancer and frontend target group (we need its DNS name for `CORS_ORIGINS`).
7. ECS cluster `leap` with Service Connect namespace `leap`.
8. ECS services, **in this order**, each one healthy before the next:
   1. `analytics-service` (its `/health` passes even before the reporting user exists)
   2. `portfolio-app` (watch its logs: Flyway migrations, then the price backfill)
   3. `frontend`, attached to the load balancer's target group
9. Create the `leap_reporting` user on the primary (see [Database](#database-rds)), now that
   Flyway has created the tables.
10. Open `http://<load-balancer-dns-name>`, log in, place a trade, check the dashboard. Log in as
    an admin or analyst and open the analytics dashboard to check the replica path.
11. Only after a manual deploy works end to end: add the deploy stages to the `Jenkinsfile`.

## Rules that keep the deployment working

- Never edit, rename or renumber a migration that has already run.
- Keep `portfolio-app` at exactly one copy.
- Keep the Service Connect names `portfolio-app` and `analytics-service`.
- Analytics reads only from the read replica. Never point `ANALYTICS_DB_HOST` at the primary.
- Log to standard output only.
- Only `/actuator/health` is public. Never set `management.endpoints.web.exposure.include=*`;
  `/actuator/env` and `/actuator/heapdump` would expose our secrets.
- No keys, passwords or secrets in the repository.



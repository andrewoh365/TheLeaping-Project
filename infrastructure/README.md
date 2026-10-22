# LEAP on AWS: deployment blueprint

This is the plan for running this project on AWS. Nothing here exists in AWS yet: we are still waiting
for our AWS account. Values in `<angle brackets>` get filled in once it arrives.

Everything below was checked against the code, and the container sizes and startup times were
measured, so creating the AWS resources should mostly be copying from this page.

## Architecture

```
Browser ──► Application Load Balancer (port 80, later 443)
                 │
                 ▼
            frontend (nginx) ──/api/──► portfolio-app (Spring) ──► analytics-service (FastAPI)
                                              │                          │
                                              └──────► RDS PostgreSQL ◄──┘
```

- Everything runs in one **ECS cluster** (`leap`) on **Fargate** (Linux, x86_64).
- Only the **frontend** is behind the load balancer. nginx forwards `/api/` to Spring, exactly as in
  `docker-compose.yaml`.
- The containers find each other through **ECS Service Connect** (namespace `leap`) using the
  **same names and ports as docker-compose**: `portfolio-app:8080` and `analytics-service:8000`.
  This keeps `frontend/nginx.conf` and the local setup unchanged.
- `postgres` from docker-compose becomes **RDS**.

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
| `CORS_ORIGINS` | `http://<load-balancer-dns-name>` (later `https://<domain>`) | plain |
| `SPRING_PROFILES_ACTIVE` | `prod` | plain |
| `TRADING_MARKETOVERRIDE_ALLOWED` | `true` only if we want the after-hours trading toggle for demos | optional |

`SPRING_PROFILES_ACTIVE=prod` just replaces the `local` default from `application.yaml`; there is
no `application-prod.yaml`. Without `JWT_SECRET` Spring refuses to start, which is intended.

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
| `DB_HOST` | `<rds-endpoint>` | plain |
| `DB_PORT` | `5432` | plain |
| `DB_NAME` | `leaping_db` | plain |
| `DB_USERNAME` | from the RDS secret, key `username` | secret |
| `DB_PASSWORD` | from the RDS secret, key `password` | secret |

**Health check:** the image has neither `curl` nor `wget`, so it uses Python itself. `/ready`
answers 503 when the database is unreachable, which makes the command fail.

```json
"healthCheck": {
  "command": ["CMD", "python", "-c", "import urllib.request; urllib.request.urlopen('http://localhost:8000/ready', timeout=4)"],
  "interval": 15,
  "timeout": 5,
  "retries": 3,
  "startPeriod": 30
}
```

Stateless, so it is the safe service to try autoscaling on (for example 1 to 3 copies on CPU).

## Database (RDS)

| Setting | Value |
|---|---|
| Engine | PostgreSQL **16** (same as `postgres:16` in docker-compose) |
| Initial database name | `leaping_db` |
| Instance | `db.t4g.micro`, single AZ |
| Storage | 20 GB gp3 |
| Credentials | "Manage master credentials in AWS Secrets Manager" |
| Public access | **No** |
| Automated backups | 7 days |

- RDS requires encrypted connections by default. Both database drivers (Java and Python) handle
  that automatically, so no extra settings are needed.
- Migration V7 creates the `pgcrypto` extension. RDS supports it and the master user may create it.
- **Never edit, rename or renumber a migration that has already run.** Add a new one instead.
  A renumbered migration stops Spring from starting, and unlike our local databases RDS cannot just
  be wiped.

## Secrets

Stored in **AWS Secrets Manager** and injected by ECS as environment variables. Never in the
repository, which is public.

| Secret | Used by | How to create |
|---|---|---|
| RDS master secret (created by RDS) | portfolio-app, analytics-service | automatically when the database is created |
| `leap/jwt-secret` | portfolio-app | new value from `openssl rand -base64 64` |

`JWT_SECRET` must be at least 64 characters because tokens are signed with HS512. Do not reuse
the development or test values from `.env.example`, the `Jenkinsfile` or old commits.

## Networking

- One VPC with subnets in **two availability zones** (the load balancer requires two).
- **Open decision**, see [Decisions still open](#decisions-still-open): containers in private
  subnets with a NAT Gateway, or in public subnets with public IPs.

**Security groups** (all outbound traffic allowed):

| Security group | Allows in |
|---|---|
| `leap-alb` | port 80 (later 443) from anywhere |
| `leap-frontend` | port 80 from `leap-alb` |
| `leap-portfolio-app` | port 8080 from `leap-frontend` |
| `leap-analytics` | port 8000 from `leap-portfolio-app` |
| `leap-rds` | port 5432 from `leap-portfolio-app` and `leap-analytics` |

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
| Task execution role | lets ECS pull images, write logs and read our secrets | `AmazonECSTaskExecutionRolePolicy` + `secretsmanager:GetSecretValue` on the two secrets |
| Task role | lets our code call AWS | not needed: none of our code calls AWS |
| Jenkins deploy user | lets Jenkins push images and deploy | ECR push (`AmazonEC2ContainerRegistryPowerUser`), `ecs:RegisterTaskDefinition`, `ecs:DescribeTaskDefinition`, `ecs:UpdateService`, `ecs:DescribeServices`, `iam:PassRole` on the task execution role |

If our account turns out to be an AWS Academy Learner Lab, we cannot create roles or users: use
the provided `LabRole` for the ECS roles, and the lab's temporary keys for Jenkins.

Jenkins stores the deploy keys in **Manage Jenkins → Credentials** and only uses them in deploy
stages that run on `main` after someone approves. Never in the `Jenkinsfile` or the repository.

## First deployment, in order

1. **Billing alert** (AWS Budgets) before anything else.
2. VPC, subnets and security groups.
3. RDS. Wait until its status is *Available*.
4. `leap/jwt-secret` in Secrets Manager.
5. ECR repositories, then push the three images by hand from the Jenkins VM.
6. Load balancer and frontend target group (we need its DNS name for `CORS_ORIGINS`).
7. ECS cluster `leap` with Service Connect namespace `leap`.
8. ECS services, **in this order**, each one healthy before the next:
   1. `analytics-service`
   2. `portfolio-app` (watch its logs: Flyway migrations, then the price backfill)
   3. `frontend`, attached to the load balancer's target group
9. Open `http://<load-balancer-dns-name>`, log in, place a trade, check the dashboard.
10. Only after a manual deploy works end to end: add the deploy stages to the `Jenkinsfile`.

## Rules that keep the deployment working

- Never edit, rename or renumber a migration that has already run.
- Keep `portfolio-app` at exactly one copy.
- Keep the Service Connect names `portfolio-app` and `analytics-service`.
- Log to standard output only.
- Only `/actuator/health` is public. Never set `management.endpoints.web.exposure.include=*`;
  `/actuator/env` and `/actuator/heapdump` would expose our secrets.
- No keys, passwords or secrets in the repository.



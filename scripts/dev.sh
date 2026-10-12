#!/usr/bin/env bash
#
# Local dev launcher for the Leaping trading platform.
#
#   scripts/dev.sh              Reset + reseed the DB, then start backend and frontend
#   scripts/dev.sh --no-reset   Start backend and frontend against the existing DB
#   scripts/dev.sh --reset-only Reset + reseed the DB, then stop (nothing left running)
#   scripts/dev.sh --no-seed-users  Skip the DataSeeder demo customers (Flyway seed only)
#
# Reset = drop and recreate leaping_db. The schema and reference data are then
# rebuilt by Flyway (V1..Vn) on backend startup, and the 'dev' profile's
# DataSeeder adds the demo customers, so every reset produces the same data.
#
# DB credentials are read from backend/portfolio-app/src/main/resources/application-local.yaml
# (falls back to DB_USERNAME / DB_PASSWORD env vars, then postgres/<none>).
#
# Ctrl-C stops both servers. Logs are written to logs/backend.log and logs/frontend.log.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend/portfolio-app"
FRONTEND_DIR="$ROOT_DIR/frontend"
LOCAL_CONFIG="$BACKEND_DIR/src/main/resources/application-local.yaml"
LOG_DIR="$ROOT_DIR/logs"

BACKEND_PORT=8080
FRONTEND_PORT=4200
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="leaping_db"

RESET_DB=true
START_SERVERS=true
SEED_USERS=true

for arg in "$@"; do
  case "$arg" in
    --no-reset)      RESET_DB=false ;;
    --reset-only)    START_SERVERS=false ;;
    --no-seed-users) SEED_USERS=false ;;
    -h|--help)       sed -n '3,19p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown option: $arg (see --help)"; exit 1 ;;
  esac
done

log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m==>\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m==>\033[0m %s\n' "$*" >&2; exit 1; }

package_install_hint() {
  local package_name=$1
  if command -v brew >/dev/null 2>&1; then
    printf 'brew install %s' "$package_name"
  elif command -v apt-get >/dev/null 2>&1; then
    printf 'sudo apt-get install -y %s' "$package_name"
  elif command -v dnf >/dev/null 2>&1; then
    printf 'sudo dnf install -y %s' "$package_name"
  elif command -v yum >/dev/null 2>&1; then
    printf 'sudo yum install -y %s' "$package_name"
  elif command -v zypper >/dev/null 2>&1; then
    printf 'sudo zypper install %s' "$package_name"
  elif command -v pacman >/dev/null 2>&1; then
    printf 'sudo pacman -S %s' "$package_name"
  elif command -v apk >/dev/null 2>&1; then
    printf 'sudo apk add %s' "$package_name"
  else
    printf 'install %s with your system package manager' "$package_name"
  fi
}

postgres_start_hint() {
  if command -v brew >/dev/null 2>&1; then
    printf 'brew services start postgresql@17'
  elif command -v systemctl >/dev/null 2>&1; then
    printf 'sudo systemctl start postgresql'
  elif command -v service >/dev/null 2>&1; then
    printf 'sudo service postgresql start'
  else
    printf 'start your local PostgreSQL service'
  fi
}

# --- Config -----------------------------------------------------------------

# Pull a value from the spring.datasource block of application-local.yaml.
read_local_config() {
  [[ -f "$LOCAL_CONFIG" ]] || return 0
  awk -v key="$1" '
    /^[^[:space:]#]/ { in_ds = 0 }
    /^[[:space:]]+datasource:/ { in_ds = 1; next }
    in_ds && $1 == key":" {
      sub(/^[^:]*:[[:space:]]*/, ""); sub(/[[:space:]]+#.*$/, "")
      gsub(/^["'\'']|["'\'']$/, ""); print; exit
    }' "$LOCAL_CONFIG"
}

# Resolve Spring-style placeholders like ${DB_USERNAME:postgres} or ${DB_PASSWORD}.
resolve_spring_placeholder() {
  local raw="$1"
  local var_name default_value resolved_value

  if [[ "$raw" =~ ^\$\{([A-Za-z_][A-Za-z0-9_]*)(:([^}]*))?\}$ ]]; then
    var_name="${BASH_REMATCH[1]}"
    default_value="${BASH_REMATCH[3]:-}"
    resolved_value="${!var_name:-}"

    if [[ -n "$resolved_value" ]]; then
      printf '%s' "$resolved_value"
    else
      printf '%s' "$default_value"
    fi
    return 0
  fi

  printf '%s' "$raw"
}

[[ -f "$LOCAL_CONFIG" ]] || die "Missing $LOCAL_CONFIG - copy application-local.yaml.example and fill it in."

DB_USER_RAW="$(read_local_config username)"
DB_PASS_RAW="$(read_local_config password)"

DB_USER="${DB_USERNAME:-$(resolve_spring_placeholder "$DB_USER_RAW")}"
DB_USER="${DB_USER:-postgres}"
DB_PASS="${DB_PASSWORD:-$(resolve_spring_placeholder "$DB_PASS_RAW")}"

# Keep backend placeholder resolution aligned with what this script used.
export DB_USERNAME="${DB_USERNAME:-$DB_USER}"
export DB_PASSWORD="${DB_PASSWORD:-$DB_PASS}"
export JWT_SECRET="${JWT_SECRET:-local-dev-jwt-secret-change-me}"
export PGPASSWORD="$DB_PASS"

psql_admin() { psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -v ON_ERROR_STOP=1 -qAt "$@"; }

# --- Ports ------------------------------------------------------------------

# Kill whatever is LISTENING on a port (not clients connected to it, e.g. a browser tab).
free_port() {
  local port=$1 pids
  pids=$(lsof -ti "tcp:$port" -sTCP:LISTEN 2>/dev/null || true)
  [[ -z "$pids" ]] && return 0

  warn "Port $port in use by PID(s) $(echo $pids | tr '\n' ' ')- stopping"
  kill $pids 2>/dev/null || true
  for _ in {1..10}; do
    sleep 0.5
    pids=$(lsof -ti "tcp:$port" -sTCP:LISTEN 2>/dev/null || true)
    [[ -z "$pids" ]] && return 0
  done
  warn "Port $port still busy - force killing"
  kill -9 $pids 2>/dev/null || true
  sleep 0.5
  [[ -z "$(lsof -ti "tcp:$port" -sTCP:LISTEN 2>/dev/null || true)" ]] || die "Could not free port $port"
}

wait_for_port() {
  local port=$1 name=$2 pid=$3 logfile=$4 timeout=$5
  for ((i = 0; i < timeout; i++)); do
    if lsof -ti "tcp:$port" -sTCP:LISTEN >/dev/null 2>&1; then return 0; fi
    if ! kill -0 "$pid" 2>/dev/null; then
      tail -n 40 "$logfile" >&2
      die "$name exited during startup - see $logfile"
    fi
    sleep 1
  done
  tail -n 40 "$logfile" >&2
  die "$name did not start on port $port within ${timeout}s - see $logfile"
}

# --- Processes --------------------------------------------------------------

BACKEND_PID=""
FRONTEND_PID=""

cleanup() {
  trap - INT TERM EXIT
  echo
  log "Shutting down..."
  for pid in $FRONTEND_PID $BACKEND_PID; do
    # Kill the whole process group so mvn's forked JVM and ng's children go too.
    kill -- "-$pid" 2>/dev/null || kill "$pid" 2>/dev/null || true
  done
  free_port "$BACKEND_PORT"
  free_port "$FRONTEND_PORT"
}

start_backend() {
  local profiles="local"
  $SEED_USERS && profiles="local,dev"
  log "Starting backend (profiles: $profiles) -> logs/backend.log"
  (cd "$BACKEND_DIR" && SPRING_PROFILES_ACTIVE="$profiles" exec ./mvnw -q spring-boot:run) \
    >"$LOG_DIR/backend.log" 2>&1 &
  BACKEND_PID=$!
  wait_for_port "$BACKEND_PORT" "Backend" "$BACKEND_PID" "$LOG_DIR/backend.log" 180

  # The port opens before CommandLineRunners finish, so wait for the seeder explicitly.
  if $SEED_USERS && $RESET_DB; then
    for _ in {1..60}; do
      grep -q "Data seeding completed" "$LOG_DIR/backend.log" && break
      kill -0 "$BACKEND_PID" 2>/dev/null || die "Backend exited while seeding - see logs/backend.log"
      sleep 1
    done
    grep -q "Data seeding completed" "$LOG_DIR/backend.log" || die "DataSeeder did not finish - see logs/backend.log"
  fi
  log "Backend up on http://localhost:$BACKEND_PORT"
}

start_frontend() {
  if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
    log "Installing frontend dependencies (first run)..."
    (cd "$FRONTEND_DIR" && npm install --no-audit --no-fund)
  fi
  log "Starting frontend -> logs/frontend.log"
  (cd "$FRONTEND_DIR" && exec npx ng serve --port "$FRONTEND_PORT") \
    >"$LOG_DIR/frontend.log" 2>&1 &
  FRONTEND_PID=$!
  wait_for_port "$FRONTEND_PORT" "Frontend" "$FRONTEND_PID" "$LOG_DIR/frontend.log" 180
  log "Frontend up on http://localhost:$FRONTEND_PORT"
}

reset_db() {
  log "Resetting database '$DB_NAME' (user: $DB_USER)"
  # Close lingering connections (pgAdmin, IDE, etc.) so the drop can't be blocked.
  psql_admin -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity
                 WHERE datname = '$DB_NAME' AND pid <> pg_backend_pid();" >/dev/null
  psql_admin -c "DROP DATABASE IF EXISTS $DB_NAME;"
  psql_admin -c "CREATE DATABASE $DB_NAME OWNER \"$DB_USER\";"
}

# --- Main -------------------------------------------------------------------

command -v psql >/dev/null || die "psql not found ($(package_install_hint postgresql))"
command -v lsof >/dev/null || die "lsof not found ($(package_install_hint lsof))"
command -v npm  >/dev/null || die "npm not found (install Node.js)"

pg_isready -h "$DB_HOST" -p "$DB_PORT" >/dev/null 2>&1 \
  || die "Postgres is not accepting connections on $DB_HOST:$DB_PORT ($(postgres_start_hint))"

mkdir -p "$LOG_DIR"
set -m  # run background jobs in their own process groups so cleanup can kill them whole

log "Freeing ports $BACKEND_PORT and $FRONTEND_PORT"
free_port "$BACKEND_PORT"
free_port "$FRONTEND_PORT"

trap cleanup INT TERM EXIT

$RESET_DB && reset_db

if ! $START_SERVERS; then
  # Boot the backend just long enough for Flyway + DataSeeder to run, then stop.
  start_backend
  log "Database reset and reseeded."
  exit 0
fi

start_backend
start_frontend

if $SEED_USERS; then
  log "Demo logins: john.trader@example.com / password123, sarah.investor@example.com / password456, demo@example.com / password789"
fi
log "App ready at http://localhost:$FRONTEND_PORT  (Ctrl-C to stop both)"

# Exit (and clean up) as soon as either server dies.
while kill -0 "$BACKEND_PID" 2>/dev/null && kill -0 "$FRONTEND_PID" 2>/dev/null; do
  sleep 2
done
warn "A server exited unexpectedly - check logs/backend.log and logs/frontend.log"
exit 1

#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/compose.yaml"
FIXTURE_FILE="${SCRIPT_DIR}/fixture.sql"
PYTHON_RUNNER="${SCRIPT_DIR}/benchmark.py"

PROJECT="${BENCHMARK_PROJECT:-leap-analytics-benchmark}"
SPRING_PORT="${BENCHMARK_SPRING_PORT:-18080}"
ANALYTICS_PORT="${BENCHMARK_ANALYTICS_PORT:-18000}"
MIN_AVAILABLE_MB="${BENCHMARK_MIN_AVAILABLE_MB:-3072}"
OUTPUT_DIR="${BENCHMARK_OUTPUT_DIR:-${SCRIPT_DIR}/results/$(date -u +%Y%m%dT%H%M%SZ)}"

export BENCHMARK_PROJECT="${PROJECT}"
export BENCHMARK_COMPOSE_FILE="${COMPOSE_FILE}"
export BENCHMARK_SPRING_PORT="${SPRING_PORT}"
export BENCHMARK_ANALYTICS_PORT="${ANALYTICS_PORT}"
export BENCHMARK_DB_USERNAME="${BENCHMARK_DB_USERNAME:-benchmark_primary}"
export BENCHMARK_DB_PASSWORD="${BENCHMARK_DB_PASSWORD:-benchmark-primary-password}"
export BENCHMARK_REPLICATION_USER="${BENCHMARK_REPLICATION_USER:-benchmark_replication}"
export BENCHMARK_REPLICATION_PASSWORD="${BENCHMARK_REPLICATION_PASSWORD:-benchmark-replication-password}"
export BENCHMARK_REPORTING_USER="${BENCHMARK_REPORTING_USER:-benchmark_reporting}"
export BENCHMARK_REPORTING_PASSWORD="${BENCHMARK_REPORTING_PASSWORD:-benchmark-reporting-password}"
export BENCHMARK_REPLICATION_SLOT="${BENCHMARK_REPLICATION_SLOT:-benchmark_reporting_slot}"
export BENCHMARK_JWT_SECRET="${BENCHMARK_JWT_SECRET:-benchmark-jwt-secret-only-for-disposable-local-runs-2026}"
export BENCHMARK_ANALYTICS_TOKEN="${BENCHMARK_ANALYTICS_TOKEN:-benchmark-analytics-token-only-for-disposable-local-runs}"
export BENCHMARK_TRADER_PASSWORD="${BENCHMARK_TRADER_PASSWORD:-BenchmarkTrader123!}"
export BENCHMARK_ANALYST_PASSWORD="${BENCHMARK_ANALYST_PASSWORD:-BenchmarkAnalyst123!}"

COMPOSE=(docker-compose -p "${PROJECT}" -f "${COMPOSE_FILE}")
STACK_STARTED=0

fail() {
    printf 'ERROR: %s\n' "$1" >&2
    exit 1
}

check_port_free() {
    python3 - "$1" <<'PY'
import socket
import sys

port = int(sys.argv[1])
with socket.socket() as sock:
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    if sock.connect_ex(("127.0.0.1", port)) == 0:
        raise SystemExit(1)
PY
}

check_resources() {
    local available_mb
    local cpu_count

    available_mb="$(awk '/MemAvailable:/ { print int($2 / 1024) }' /proc/meminfo)"
    cpu_count="$(nproc)"

    printf 'Host capacity: %s CPUs, %s MiB available memory\n' "${cpu_count}" "${available_mb}"
    printf 'Benchmark runtime caps: 1.50 CPUs, 2,816 MiB across application services\n'

    if (( cpu_count < 2 )); then
        fail "At least 2 host CPUs are required for the capped benchmark stack."
    fi

    if (( available_mb < MIN_AVAILABLE_MB )); then
        fail "Only ${available_mb} MiB is available; refusing to start. Need at least ${MIN_AVAILABLE_MB} MiB while the development stack remains running."
    fi
}

check_namespace() {
    local existing_containers
    local existing_network
    local existing_volumes

    [[ "${PROJECT}" =~ ^[a-z0-9][a-z0-9_-]+$ ]] \
        || fail "BENCHMARK_PROJECT must contain only lowercase letters, digits, underscores, and hyphens."

    existing_containers="$(docker ps -aq --filter "label=com.docker.compose.project=${PROJECT}")"
    [[ -z "${existing_containers}" ]] \
        || fail "Compose project ${PROJECT} already owns containers; refusing to touch them. Choose another BENCHMARK_PROJECT."

    existing_volumes="$(docker volume ls -q --filter "label=com.docker.compose.project=${PROJECT}")"
    [[ -z "${existing_volumes}" ]] \
        || fail "Compose project ${PROJECT} already owns volumes; refusing to touch them. Choose another BENCHMARK_PROJECT."

    existing_network="${PROJECT}_data"
    if docker network inspect "${existing_network}" >/dev/null 2>&1; then
        fail "Network ${existing_network} already exists; refusing to touch it. Choose another BENCHMARK_PROJECT."
    fi
}

preflight() {
    command -v docker >/dev/null 2>&1 || fail "docker is required."
    command -v docker-compose >/dev/null 2>&1 || fail "docker-compose v2 is required; the docker Compose plugin is not installed."
    command -v python3 >/dev/null 2>&1 || fail "python3 is required."
    command -v curl >/dev/null 2>&1 || fail "curl is required for service readiness checks."
    docker info >/dev/null 2>&1 || fail "Docker daemon is not available."
    [[ -f "${COMPOSE_FILE}" ]] || fail "Missing ${COMPOSE_FILE}."
    [[ -f "${FIXTURE_FILE}" ]] || fail "Missing ${FIXTURE_FILE}."
    [[ -f "${PYTHON_RUNNER}" ]] || fail "Missing ${PYTHON_RUNNER}."

    check_namespace
    check_port_free "${SPRING_PORT}" \
        || fail "Spring benchmark port ${SPRING_PORT} is already in use."
    check_port_free "${ANALYTICS_PORT}" \
        || fail "Analytics benchmark port ${ANALYTICS_PORT} is already in use."
    check_resources

    "${COMPOSE[@]}" config >/dev/null \
        || fail "The isolated Compose configuration is invalid."
}

cleanup() {
    if (( STACK_STARTED == 1 )); then
        "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
    fi
}

trap cleanup EXIT INT TERM

wait_for_stack() {
    local deadline=$((SECONDS + 180))

    while (( SECONDS < deadline )); do
        if curl -fsS "http://127.0.0.1:${SPRING_PORT}/api/auth/health" >/dev/null 2>&1 \
            && curl -fsS "http://127.0.0.1:${ANALYTICS_PORT}/ready" \
                -H "X-Analytics-Internal-Token: ${BENCHMARK_ANALYTICS_TOKEN}" \
                >/dev/null 2>&1; then
            return 0
        fi
        sleep 2
    done

    "${COMPOSE[@]}" ps >&2 || true
    "${COMPOSE[@]}" logs --tail=80 primary replica portfolio analytics >&2 || true
    fail "The isolated benchmark services did not become ready within 180 seconds."
}

start_stack() {
    STACK_STARTED=1
    "${COMPOSE[@]}" up -d --build
    wait_for_stack
}

stop_stack() {
    "${COMPOSE[@]}" down --volumes --remove-orphans
    STACK_STARTED=0
}

seed_fixture() {
    "${COMPOSE[@]}" exec -T \
        -e "PGPASSWORD=${BENCHMARK_DB_PASSWORD}" \
        primary psql -X -v ON_ERROR_STOP=1 \
        -U "${BENCHMARK_DB_USERNAME}" -d leaping_db < "${FIXTURE_FILE}"
}

find_btc_instrument() {
    "${COMPOSE[@]}" exec -T \
        -e "PGPASSWORD=${BENCHMARK_DB_PASSWORD}" \
        primary psql -X -v ON_ERROR_STOP=1 -At \
        -U "${BENCHMARK_DB_USERNAME}" -d leaping_db \
        -c "SELECT instrument_id FROM instruments WHERE symbol = 'BTC-USD' ORDER BY instrument_id LIMIT 1" \
        | awk 'NF { value=$1 } END { if (value == "") exit 1; print value }'
}

run_scenario() {
    local scenario="$1"
    local instrument_id

    start_stack
    seed_fixture
    instrument_id="$(find_btc_instrument)"

    BENCHMARK_SCENARIO="${scenario}" \
    BENCHMARK_BTC_INSTRUMENT_ID="${instrument_id}" \
    python3 "${PYTHON_RUNNER}" run \
        --scenario "${scenario}" \
        --output "${OUTPUT_DIR}/${scenario}.json"

    stop_stack
}

usage() {
    cat <<'EOF'
Usage:
  ./benchmarks/analytics-isolation/run_benchmark.sh
  ./benchmarks/analytics-isolation/run_benchmark.sh --check

The default run creates two fresh disposable Compose stacks, one per scenario.
It refuses to start when the host has insufficient available memory or when the
requested project namespace or host ports already exist.
EOF
}

main() {
    case "${1:-}" in
        "") ;;
        --check) preflight; printf 'Preflight passed; no containers were started.\n'; return 0 ;;
        --help|-h) usage; return 0 ;;
        *) usage >&2; return 2 ;;
    esac

    preflight
    mkdir -p "${OUTPUT_DIR}"
    printf 'Results directory: %s\n' "${OUTPUT_DIR}"

    run_scenario baseline
    run_scenario with_analytics

    python3 "${PYTHON_RUNNER}" compare \
        "${OUTPUT_DIR}/baseline.json" \
        "${OUTPUT_DIR}/with_analytics.json" \
        --output "${OUTPUT_DIR}/comparison.json"

    printf 'Benchmark completed. Results are in %s\n' "${OUTPUT_DIR}"
}

main "$@"

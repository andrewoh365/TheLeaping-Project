#!/usr/bin/env bash

# ============================================================
# LEAP shared script helpers
# ------------------------------------------------------------
# This file is sourced by setup.sh, start.sh, stop.sh,
# reset.sh, status.sh, and test.sh.
#
# Do NOT run this file directly.
# ============================================================


# ------------------------------------------------------------
# Project paths
# ------------------------------------------------------------

COMMON_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${COMMON_DIR}/../.." && pwd)"

SPRING_DIR="${REPO_ROOT}/backend/portfolio-app"
ANALYTICS_DIR="${REPO_ROOT}/backend/analytics-service"
FRONTEND_DIR="${REPO_ROOT}/frontend"

ENV_FILE="${REPO_ROOT}/.env"

SPRING_LOCAL_CONFIG="${SPRING_DIR}/src/main/resources/application-local.yaml"
SPRING_LOCAL_CONFIG_EXAMPLE="${SPRING_DIR}/src/main/resources/application-local.yaml.example"

RUN_DIR="${REPO_ROOT}/.run"


# ------------------------------------------------------------
# LEAP service names
# ------------------------------------------------------------

POSTGRES_CONTAINER="leap-postgres"
ANALYTICS_CONTAINER="leap-analytics-service"
ANALYTICS_IMAGE="leap-analytics"


# ------------------------------------------------------------
# Standard LEAP development ports
# ------------------------------------------------------------

POSTGRES_PORT=5432
SPRING_PORT=8081
ANALYTICS_PORT=8000
FRONTEND_PORT=4200


# ------------------------------------------------------------
# Terminal output helpers
# ------------------------------------------------------------

if [[ -t 1 ]]; then
    BOLD="\033[1m"
    GREEN="\033[0;32m"
    YELLOW="\033[0;33m"
    RED="\033[0;31m"
    BLUE="\033[0;34m"
    RESET="\033[0m"
else
    BOLD=""
    GREEN=""
    YELLOW=""
    RED=""
    BLUE=""
    RESET=""
fi


section() {
    echo
    echo -e "${BOLD}========================================${RESET}"
    echo -e "${BOLD}$1${RESET}"
    echo -e "${BOLD}========================================${RESET}"
}


info() {
    echo -e "${BLUE}→${RESET} $1"
}


success() {
    echo -e "${GREEN}✓${RESET} $1"
}


warn() {
    echo -e "${YELLOW}⚠${RESET} $1"
}


error() {
    echo -e "${RED}✗${RESET} $1" >&2
}


die() {
    error "$1"
    exit 1
}


# ------------------------------------------------------------
# Runtime directory
# ------------------------------------------------------------

ensure_run_dir() {
    mkdir -p "${RUN_DIR}"
}


# ------------------------------------------------------------
# Command checks
# ------------------------------------------------------------

command_exists() {
    command -v "$1" >/dev/null 2>&1
}


require_command() {
    local command_name="$1"
    local friendly_name="${2:-$1}"

    if ! command_exists "${command_name}"; then
        die "${friendly_name} is required but was not found."
    fi
}


# ------------------------------------------------------------
# .env helpers
# ------------------------------------------------------------

load_env() {
    if [[ ! -f "${ENV_FILE}" ]]; then
        die "Missing ${ENV_FILE}

Create a .env file in the repository root containing:

DB_USERNAME=...
DB_PASSWORD=...
JWT_SECRET=..."
    fi

    set -a

    # shellcheck disable=SC1090
    source "${ENV_FILE}"

    set +a
}


require_env_var() {
    local variable_name="$1"

    if [[ -z "${!variable_name:-}" ]]; then
        die "${variable_name} is missing or empty in ${ENV_FILE}"
    fi
}


validate_required_env() {
    require_env_var "DB_USERNAME"
    require_env_var "DB_PASSWORD"
    require_env_var "JWT_SECRET"
}


# ------------------------------------------------------------
# Docker helpers
# ------------------------------------------------------------

docker_is_running() {
    docker info >/dev/null 2>&1
}


docker_container_exists() {
    local container_name="$1"

    docker container inspect "${container_name}" >/dev/null 2>&1
}


docker_container_running() {
    local container_name="$1"

    [[ "$(docker inspect \
        --format='{{.State.Running}}' \
        "${container_name}" 2>/dev/null)" == "true" ]]
}


# ------------------------------------------------------------
# PID helpers
# ------------------------------------------------------------

pid_is_running() {
    local pid="$1"

    [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null
}


read_pid_file() {
    local pid_file="$1"

    if [[ -f "${pid_file}" ]]; then
        cat "${pid_file}"
    fi
}


remove_stale_pid_file() {
    local pid_file="$1"

    if [[ ! -f "${pid_file}" ]]; then
        return 0
    fi

    local pid
    pid="$(cat "${pid_file}")"

    if ! pid_is_running "${pid}"; then
        rm -f "${pid_file}"
        return 0
    fi

    return 1
}


# ------------------------------------------------------------
# Port helpers
# ------------------------------------------------------------

port_is_in_use() {
    local port="$1"

    if command_exists ss; then
        ss -ltnH 2>/dev/null \
            | awk '{print $4}' \
            | grep -Eq "(^|:|\])${port}$"
        return $?
    fi

    if command_exists lsof; then
        lsof -nP \
            -iTCP:"${port}" \
            -sTCP:LISTEN \
            >/dev/null 2>&1
        return $?
    fi

    if command_exists netstat; then
        netstat -ltn 2>/dev/null \
            | awk '{print $4}' \
            | grep -Eq "(^|:|\])${port}$"
        return $?
    fi

    return 1
}


show_port_owner() {
    local port="$1"

    echo

    if command_exists lsof; then
        lsof -nP \
            -iTCP:"${port}" \
            -sTCP:LISTEN \
            2>/dev/null || true
        return
    fi

    if command_exists ss; then
        ss -ltnp 2>/dev/null \
            | grep -E ":${port}[[:space:]]" || true
        return
    fi

    warn "Unable to identify the process using port ${port}."
}


require_port_available() {
    local port="$1"
    local service_name="$2"

    if port_is_in_use "${port}"; then
        error "${service_name} cannot start because port ${port} is already in use."

        show_port_owner "${port}"

        echo
        echo "The script will NOT automatically kill this process."
        echo "Check what owns the port, stop it safely, then rerun the command."

        return 1
    fi

    return 0
}


# ------------------------------------------------------------
# Waiting / health-check helpers
# ------------------------------------------------------------

wait_for_port() {
    local port="$1"
    local service_name="$2"
    local timeout_seconds="${3:-60}"

    local elapsed=0

    while (( elapsed < timeout_seconds )); do
        if port_is_in_use "${port}"; then
            return 0
        fi

        sleep 1
        ((elapsed += 1))
    done

    error "${service_name} did not open port ${port} within ${timeout_seconds} seconds."
    return 1
}


wait_for_http() {
    local url="$1"
    local service_name="$2"
    local timeout_seconds="${3:-60}"

    local elapsed=0

    while (( elapsed < timeout_seconds )); do
        if curl \
            --silent \
            --fail \
            --max-time 2 \
            "${url}" \
            >/dev/null 2>&1; then
            return 0
        fi

        sleep 1
        ((elapsed += 1))
    done

    error "${service_name} did not become healthy within ${timeout_seconds} seconds."
    return 1
}

# ------------------------------------------------------------
# Node / nvm helpers
# ------------------------------------------------------------

load_project_node() {
    local nvmrc_file="${REPO_ROOT}/.nvmrc"

    if [[ ! -f "${nvmrc_file}" ]]; then
        die "Missing ${nvmrc_file}

Run:
  ./scripts/setup.sh"
    fi

    local desired_version
    desired_version="$(
        tr -d '[:space:]' < "${nvmrc_file}"
    )"

    export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"

    if [[ ! -s "${NVM_DIR}/nvm.sh" ]]; then
        die "nvm is not installed.

Run:
  ./scripts/setup.sh"
    fi

    # nvm does not behave well with nounset enabled while loading.
    local restore_nounset=false

    if [[ $- == *u* ]]; then
        restore_nounset=true
        set +u
    fi

    # shellcheck disable=SC1090
    source "${NVM_DIR}/nvm.sh"

    if [[ "${restore_nounset}" == "true" ]]; then
        set -u
    fi

    if [[ "$(nvm version "${desired_version}")" == "N/A" ]]; then
        die "Node ${desired_version} is not installed.

Run:
  ./scripts/setup.sh"
    fi

    nvm use "${desired_version}" >/dev/null

    hash -r

    if [[ "$(node --version)" != "v${desired_version}" ]]; then
        die "Could not activate the LEAP Node version.

Expected:
  v${desired_version}

Detected:
  $(node --version)"
    fi
}

# ------------------------------------------------------------
# Header
# ------------------------------------------------------------

print_leap_header() {
    echo
    echo -e "${BOLD}LEAP Development Environment${RESET}"
    echo "Repository: ${REPO_ROOT}"
}
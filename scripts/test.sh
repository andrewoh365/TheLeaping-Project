#!/usr/bin/env bash

set -Eeuo pipefail

# ============================================================
# LEAP standardized test suite
#
# This is the ONE team command for verifying a branch.
#
# It runs:
#
#   1. Spring Boot tests
#      - temporary PostgreSQL 16 test database
#      - Maven Wrapper
#      - Testcontainers where individual tests require it
#
#   2. Python analytics tests
#      - inside the standardized Python Docker image
#
#   3. Angular verification
#      - npm ci
#      - production build
#
# Usage:
#   ./scripts/test.sh
#
# This script does NOT use or modify the normal LEAP
# development database.
# ============================================================


SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/common.sh"


# ------------------------------------------------------------
# Test environment
# ------------------------------------------------------------

TEST_POSTGRES_CONTAINER="leap-test-postgres-$$"
TEST_POSTGRES_IMAGE="postgres:16"

TEST_DB_NAME="leaping_test_db"
TEST_DB_USERNAME="leap_test"
TEST_DB_PASSWORD="leap_test_password"

ANALYTICS_TEST_IMAGE="leap-analytics-test"

REQUIRED_JAVA_MAJOR=21


# ------------------------------------------------------------
# Cleanup
# ------------------------------------------------------------

cleanup() {
    if command_exists docker && docker_is_running; then

        if docker_container_exists "${TEST_POSTGRES_CONTAINER}"; then
            docker rm -f \
                "${TEST_POSTGRES_CONTAINER}" \
                >/dev/null 2>&1 || true
        fi

    fi
}


trap cleanup EXIT INT TERM


# ------------------------------------------------------------
# Environment checks
# ------------------------------------------------------------

check_java_version() {
    local java_major

    java_major="$(
        java -version 2>&1 \
            | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' \
            | head -n 1
    )"

    if [[ "${java_major}" != "${REQUIRED_JAVA_MAJOR}" ]]; then
        die "Java ${REQUIRED_JAVA_MAJOR} is required to run the LEAP test suite.

Detected:
$(java -version 2>&1 | head -n 1)"
    fi

    success "Java ${REQUIRED_JAVA_MAJOR}"
}


check_environment() {
    section "Test environment"

    require_command docker "Docker"
    require_command java "Java"

    if ! docker_is_running; then
        die "Docker daemon is not running.

Docker is required because the Spring integration tests
and analytics tests use containers."
    fi

    check_java_version

    # Always use the exact Node version pinned in .nvmrc.
    load_project_node

    require_command node "Node.js"
    require_command npm "npm"

    success "Node $(node --version)"
    success "npm $(npm --version)"

    [[ -f "${SPRING_DIR}/mvnw" ]] \
        || die "Maven wrapper was not found at ${SPRING_DIR}/mvnw"

    [[ -f "${SPRING_DIR}/pom.xml" ]] \
        || die "Spring pom.xml was not found."

    [[ -f "${ANALYTICS_DIR}/Dockerfile" ]] \
        || die "Analytics Dockerfile was not found."

    [[ -d "${ANALYTICS_DIR}/tests" ]] \
        || die "Analytics tests directory was not found."

    [[ -f "${ANALYTICS_DIR}/pytest.ini" ]] \
        || die "Analytics pytest.ini was not found."

    [[ -f "${FRONTEND_DIR}/package.json" ]] \
        || die "Frontend package.json was not found."

    [[ -f "${FRONTEND_DIR}/package-lock.json" ]] \
        || die "Frontend package-lock.json was not found."

    success "Test prerequisites verified"
}


# ------------------------------------------------------------
# Frontend safety checks
# ------------------------------------------------------------

ensure_frontend_not_running() {
    local frontend_pid_file="${RUN_DIR}/frontend.pid"
    local frontend_pid

    remove_stale_pid_file "${frontend_pid_file}" || true

    frontend_pid="$(read_pid_file "${frontend_pid_file}")"

    if [[ -n "${frontend_pid}" ]] && pid_is_running "${frontend_pid}"; then
        die "Angular frontend is currently running.

./scripts/test.sh uses npm ci, which replaces frontend node_modules.

Stop LEAP first:

  ./scripts/stop.sh

Then run:

  ./scripts/test.sh"
    fi

    if port_is_in_use "${FRONTEND_PORT}"; then
        die "Port ${FRONTEND_PORT} is currently in use.

Do not run the standardized frontend tests while a development
frontend is running.

Stop LEAP first:

  ./scripts/stop.sh

Then run:

  ./scripts/test.sh"
    fi
}


# ------------------------------------------------------------
# Temporary Spring test database
# ------------------------------------------------------------

start_test_database() {
    section "Spring test database"

    info "Creating isolated PostgreSQL 16 test database"

    docker run \
        -d \
        --rm \
        --name "${TEST_POSTGRES_CONTAINER}" \
        -e POSTGRES_DB="${TEST_DB_NAME}" \
        -e POSTGRES_USER="${TEST_DB_USERNAME}" \
        -e POSTGRES_PASSWORD="${TEST_DB_PASSWORD}" \
        -P \
        "${TEST_POSTGRES_IMAGE}" \
        >/dev/null

    info "Waiting for test database"

    local attempts=0
    local max_attempts=30

    until docker exec "${TEST_POSTGRES_CONTAINER}" \
        pg_isready \
        -U "${TEST_DB_USERNAME}" \
        -d "${TEST_DB_NAME}" \
        >/dev/null 2>&1
    do
        ((attempts += 1))

        if (( attempts >= max_attempts )); then
            die "Temporary PostgreSQL test database did not become ready."
        fi

        sleep 1
    done

    success "Temporary PostgreSQL database is ready"
}


get_test_database_port() {
    local mapped_port

    mapped_port="$(
        docker port \
            "${TEST_POSTGRES_CONTAINER}" \
            5432/tcp \
            | head -n 1 \
            | sed 's/.*://'
    )"

    if [[ -z "${mapped_port}" ]]; then
        die "Could not determine the temporary PostgreSQL host port."
    fi

    echo "${mapped_port}"
}


# ------------------------------------------------------------
# Spring tests
# ------------------------------------------------------------

run_spring_tests() {
    section "Spring Boot tests"

    start_test_database

    local test_db_port
    test_db_port="$(get_test_database_port)"

    info "Running Spring tests with Maven Wrapper"

    echo
    echo "Temporary database:"
    echo "  Database: ${TEST_DB_NAME}"
    echo "  Port:     ${test_db_port}"
    echo

    (
        cd "${SPRING_DIR}"

        export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${test_db_port}/${TEST_DB_NAME}"
        export SPRING_DATASOURCE_USERNAME="${TEST_DB_USERNAME}"
        export SPRING_DATASOURCE_PASSWORD="${TEST_DB_PASSWORD}"

        export SPRING_FLYWAY_URL="jdbc:postgresql://127.0.0.1:${test_db_port}/${TEST_DB_NAME}"
        export SPRING_FLYWAY_USER="${TEST_DB_USERNAME}"
        export SPRING_FLYWAY_PASSWORD="${TEST_DB_PASSWORD}"

        # Fixed test-only JWT secret.
        # This is intentionally NOT the developer's real local secret.
        export JWT_SECRET="LEAP_TEST_ONLY_JWT_SECRET_2026_DO_NOT_USE_OUTSIDE_AUTOMATED_TESTS_12345678901234567890"

        bash ./mvnw clean test
    )

    success "Spring Boot test suite passed"

    # Spring is finished with this temporary database.
    # Remove it immediately instead of waiting for the EXIT trap.
    if docker_container_exists "${TEST_POSTGRES_CONTAINER}"; then
        docker rm -f \
            "${TEST_POSTGRES_CONTAINER}" \
            >/dev/null
    fi

    success "Temporary Spring test database removed"
}


# ------------------------------------------------------------
# Analytics tests
# ------------------------------------------------------------

run_analytics_tests() {
    section "Analytics tests"

    info "Building standardized analytics test image"

    docker build \
        -t "${ANALYTICS_TEST_IMAGE}" \
        "${ANALYTICS_DIR}"

    success "Analytics test image built"

    info "Running pytest inside Python Docker environment"

    docker run \
        --rm \
        -v "${ANALYTICS_DIR}/tests:/app/tests:ro" \
        -v "${ANALYTICS_DIR}/pytest.ini:/app/pytest.ini:ro" \
        "${ANALYTICS_TEST_IMAGE}" \
        pytest -q

    success "Analytics test suite passed"
}


# ------------------------------------------------------------
# Frontend verification
# ------------------------------------------------------------

run_frontend_checks() {
    section "Angular frontend"

    info "Installing exact frontend dependencies with npm ci"

    (
        cd "${FRONTEND_DIR}"
        npm ci
    )

    success "Frontend dependencies match package-lock.json"

    info "Building Angular application"

    (
        cd "${FRONTEND_DIR}"
        npm run build
    )

    success "Angular application builds successfully"

    if find "${FRONTEND_DIR}/src" \
        -type f \
        -name "*.spec.ts" \
        -print -quit \
        | grep -q .; then

        echo
        warn "Angular .spec.ts tests were detected."
        warn "The current standardized suite verifies the frontend build only."
        warn "We should add a standardized headless-browser test environment before relying on Angular unit tests."
    else
        info "No Angular .spec.ts unit tests currently exist"
    fi
}


# ------------------------------------------------------------
# Final result
# ------------------------------------------------------------

print_success_summary() {
    section "All LEAP checks passed"

    success "Spring Boot tests passed"
    success "Analytics tests passed"
    success "Angular build passed"

    echo
    echo "This branch passed the standardized LEAP test workflow."
    echo
    echo "Team command:"
    echo "  ./scripts/test.sh"
    echo
}


# ------------------------------------------------------------
# Main
# ------------------------------------------------------------

main() {
    print_leap_header

    check_environment

    ensure_frontend_not_running

    run_spring_tests
    run_analytics_tests
    run_frontend_checks

    print_success_summary
}


main "$@"
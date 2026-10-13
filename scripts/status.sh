#!/usr/bin/env bash

set -Eeuo pipefail

# ============================================================
# LEAP application status
#
# Shows the current state of:
#
#   PostgreSQL
#   Spring Boot
#   Analytics
#   Angular
#
# This script does NOT start, stop, or modify anything.
#
# Usage:
#   ./scripts/status.sh
# ============================================================


SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/common.sh"


# ------------------------------------------------------------
# Runtime files
# ------------------------------------------------------------

SPRING_PID_FILE="${RUN_DIR}/spring.pid"
SPRING_LOG="${RUN_DIR}/spring.log"

FRONTEND_PID_FILE="${RUN_DIR}/frontend.pid"
FRONTEND_LOG="${RUN_DIR}/frontend.log"

LEAP_DB_NAME="leaping_db"

OVERALL_OK=true


# ------------------------------------------------------------
# PostgreSQL status
# ------------------------------------------------------------

check_postgres() {
    section "PostgreSQL"

    if ! docker_container_exists "${POSTGRES_CONTAINER}"; then
        error "Container does not exist: ${POSTGRES_CONTAINER}"
        OVERALL_OK=false

        if port_is_in_use "${POSTGRES_PORT}"; then
            warn "Something else is using port ${POSTGRES_PORT}"
            show_port_owner "${POSTGRES_PORT}"
        fi

        return
    fi


    if ! docker_container_running "${POSTGRES_CONTAINER}"; then
        error "${POSTGRES_CONTAINER} exists but is stopped"
        OVERALL_OK=false
        return
    fi

    success "${POSTGRES_CONTAINER} is running"


    if docker exec "${POSTGRES_CONTAINER}" \
        pg_isready \
        -U "${DB_USERNAME:-postgres}" \
        -d "${LEAP_DB_NAME}" \
        >/dev/null 2>&1; then

        success "PostgreSQL is accepting connections"
    else
        error "PostgreSQL container is running but is not ready"
        OVERALL_OK=false
    fi

    echo "Port: ${POSTGRES_PORT}"
}


# ------------------------------------------------------------
# Spring status
# ------------------------------------------------------------

check_spring() {
    section "Spring Boot"

    if [[ -f "${SPRING_PID_FILE}" ]]; then

        local pid
        pid="$(read_pid_file "${SPRING_PID_FILE}")"

        if pid_is_running "${pid}"; then
            success "LEAP-managed Spring process is running"
            echo "PID: ${pid}"

            if curl \
                --silent \
                --fail \
                --max-time 2 \
                "http://127.0.0.1:${SPRING_PORT}/api/auth/health" \
                >/dev/null 2>&1; then

                success "Spring health check passed"
            else
                error "Spring process is running but health check failed"
                OVERALL_OK=false

                echo
                echo "Check:"
                echo "  ${SPRING_LOG}"
            fi

            echo "Port: ${SPRING_PORT}"
            return
        fi


        warn "Spring PID file is stale"
        echo "PID file:"
        echo "  ${SPRING_PID_FILE}"

        OVERALL_OK=false
        return
    fi


    if curl \
        --silent \
        --fail \
        --max-time 2 \
        "http://127.0.0.1:${SPRING_PORT}/api/auth/health" \
        >/dev/null 2>&1; then

        warn "Spring appears to be running, but it was not started by the LEAP scripts"
        echo "Port: ${SPRING_PORT}"
        OVERALL_OK=false
        return
    fi


    if port_is_in_use "${SPRING_PORT}"; then
        error "Port ${SPRING_PORT} is occupied by another process"
        show_port_owner "${SPRING_PORT}"
        OVERALL_OK=false
        return
    fi


    error "Spring Boot is not running"
    OVERALL_OK=false
}


# ------------------------------------------------------------
# Analytics status
# ------------------------------------------------------------

check_analytics() {
    section "Analytics"

    if docker_container_exists "${ANALYTICS_CONTAINER}"; then

        if ! docker_container_running "${ANALYTICS_CONTAINER}"; then
            error "${ANALYTICS_CONTAINER} exists but is stopped"
            OVERALL_OK=false
            return
        fi

        success "${ANALYTICS_CONTAINER} is running"

        if curl \
            --silent \
            --fail \
            --max-time 2 \
            "http://127.0.0.1:${ANALYTICS_PORT}/health" \
            >/dev/null 2>&1; then

            success "Analytics health check passed"
        else
            error "Analytics container is running but health check failed"
            OVERALL_OK=false

            echo
            echo "Check:"
            echo "  docker logs ${ANALYTICS_CONTAINER}"
        fi

        echo "Port: ${ANALYTICS_PORT}"
        return
    fi


    if curl \
        --silent \
        --fail \
        --max-time 2 \
        "http://127.0.0.1:${ANALYTICS_PORT}/health" \
        >/dev/null 2>&1; then

        warn "Analytics appears to be running, but not in the standard LEAP container"
        echo "Port: ${ANALYTICS_PORT}"
        OVERALL_OK=false
        return
    fi


    if port_is_in_use "${ANALYTICS_PORT}"; then
        error "Port ${ANALYTICS_PORT} is occupied by another process"
        show_port_owner "${ANALYTICS_PORT}"
        OVERALL_OK=false
        return
    fi


    error "Analytics service is not running"
    OVERALL_OK=false
}


# ------------------------------------------------------------
# Angular status
# ------------------------------------------------------------

check_frontend() {
    section "Angular Frontend"

    if [[ -f "${FRONTEND_PID_FILE}" ]]; then

        local pid
        pid="$(read_pid_file "${FRONTEND_PID_FILE}")"

        if pid_is_running "${pid}"; then
            success "LEAP-managed Angular process is running"
            echo "PID: ${pid}"

            if curl \
                --silent \
                --fail \
                --max-time 2 \
                "http://127.0.0.1:${FRONTEND_PORT}" \
                >/dev/null 2>&1; then

                success "Angular is responding"
            else
                error "Angular process is running but is not responding"
                OVERALL_OK=false

                echo
                echo "Check:"
                echo "  ${FRONTEND_LOG}"
            fi

            echo "Port: ${FRONTEND_PORT}"
            return
        fi


        warn "Angular PID file is stale"
        echo "PID file:"
        echo "  ${FRONTEND_PID_FILE}"

        OVERALL_OK=false
        return
    fi


    if curl \
        --silent \
        --fail \
        --max-time 2 \
        "http://127.0.0.1:${FRONTEND_PORT}" \
        >/dev/null 2>&1; then

        warn "Angular appears to be running, but it was not started by the LEAP scripts"
        echo "Port: ${FRONTEND_PORT}"
        OVERALL_OK=false
        return
    fi


    if port_is_in_use "${FRONTEND_PORT}"; then
        error "Port ${FRONTEND_PORT} is occupied by another process"
        show_port_owner "${FRONTEND_PORT}"
        OVERALL_OK=false
        return
    fi


    error "Angular frontend is not running"
    OVERALL_OK=false
}


# ------------------------------------------------------------
# Final summary
# ------------------------------------------------------------

print_summary() {
    section "Overall status"

    if [[ "${OVERALL_OK}" == "true" ]]; then
        success "All LEAP services are running normally"

        echo
        echo "Frontend:"
        echo "  http://localhost:${FRONTEND_PORT}"

        return 0
    fi

    warn "One or more LEAP services need attention"

    echo
    echo "Useful commands:"
    echo "  Start:  ./scripts/start.sh"
    echo "  Stop:   ./scripts/stop.sh"
    echo "  Reset:  ./scripts/reset.sh"

    return 1
}


# ------------------------------------------------------------
# Main
# ------------------------------------------------------------

main() {
    print_leap_header
    ensure_run_dir

    # Status should still work even if .env is missing.
    if [[ -f "${ENV_FILE}" ]]; then
        set -a

        # shellcheck disable=SC1090
        source "${ENV_FILE}"

        set +a
    else
        warn ".env is missing; some database checks may be limited."
    fi


    if ! docker_is_running; then
        section "Docker"
        error "Docker daemon is not running"
        exit 1
    fi


    check_postgres
    check_spring
    check_analytics
    check_frontend

    print_summary
}


main "$@"
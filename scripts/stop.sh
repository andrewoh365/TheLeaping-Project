#!/usr/bin/env bash

set -Eeuo pipefail

# ============================================================
# LEAP application shutdown
#
# Safely stops only LEAP-owned services:
#
#   Angular
#   Analytics
#   Spring Boot
#   PostgreSQL
#
# Usage:
#   ./scripts/stop.sh
# ============================================================


SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/common.sh"


# ------------------------------------------------------------
# Runtime files
# ------------------------------------------------------------

SPRING_PID_FILE="${RUN_DIR}/spring.pid"
FRONTEND_PID_FILE="${RUN_DIR}/frontend.pid"


# ------------------------------------------------------------
# Managed process shutdown
# ------------------------------------------------------------

stop_managed_process() {
    local service_name="$1"
    local pid_file="$2"

    if [[ ! -f "${pid_file}" ]]; then
        info "${service_name} was not started by the LEAP scripts"
        return 0
    fi


    local pid
    pid="$(read_pid_file "${pid_file}")"


    if [[ -z "${pid}" ]]; then
        warn "${service_name} PID file is empty"
        rm -f "${pid_file}"
        return 0
    fi


    if ! pid_is_running "${pid}"; then
        warn "${service_name} is already stopped"
        rm -f "${pid_file}"
        return 0
    fi


    info "Stopping ${service_name} (PID ${pid})"


    # start.sh uses setsid when available, which gives the LEAP
    # process its own process group. Killing that group also stops
    # child processes such as Maven/Java or npm/Angular.
    local process_group
    process_group="$(
        ps -o pgid= -p "${pid}" 2>/dev/null \
            | tr -d '[:space:]' \
            || true
    )"


    if [[ -n "${process_group}" && "${process_group}" == "${pid}" ]]; then
        kill -TERM -- "-${process_group}" 2>/dev/null || true
    else
        kill -TERM "${pid}" 2>/dev/null || true
    fi


    local attempts=0
    local max_attempts=15

    while pid_is_running "${pid}" && (( attempts < max_attempts )); do
        sleep 1
        ((attempts += 1))
    done


    if pid_is_running "${pid}"; then
        warn "${service_name} did not stop gracefully; forcing shutdown"

        if [[ -n "${process_group}" && "${process_group}" == "${pid}" ]]; then
            kill -KILL -- "-${process_group}" 2>/dev/null || true
        else
            kill -KILL "${pid}" 2>/dev/null || true
        fi
    fi


    rm -f "${pid_file}"

    success "${service_name} stopped"
}


# ------------------------------------------------------------
# Angular
# ------------------------------------------------------------

stop_frontend() {
    section "Angular Frontend"

    stop_managed_process \
        "Angular frontend" \
        "${FRONTEND_PID_FILE}"


    # We intentionally do NOT kill anything merely because it is
    # using port 4200. It could belong to another project.
    if port_is_in_use "${FRONTEND_PORT}"; then
        warn "Port ${FRONTEND_PORT} is still in use"

        show_port_owner "${FRONTEND_PORT}"

        echo
        warn "The remaining process was not stopped because LEAP does not own it."
    fi
}


# ------------------------------------------------------------
# Analytics
# ------------------------------------------------------------

stop_analytics() {
    section "Analytics"

    if ! command_exists docker; then
        warn "Docker is unavailable; analytics container could not be checked"
        return 0
    fi


    if ! docker_is_running; then
        warn "Docker daemon is not running; analytics container could not be checked"
        return 0
    fi


    if ! docker_container_exists "${ANALYTICS_CONTAINER}"; then
        info "Analytics container is already stopped"
        return 0
    fi


    info "Stopping ${ANALYTICS_CONTAINER}"

    docker stop \
        --time 10 \
        "${ANALYTICS_CONTAINER}" \
        >/dev/null 2>&1 || true


    # start.sh creates this container with --rm, so normally it
    # disappears automatically after being stopped. If it does
    # remain for any reason, it is still safe to remove because
    # this exact named container belongs to LEAP.
    if docker_container_exists "${ANALYTICS_CONTAINER}"; then
        docker rm -f \
            "${ANALYTICS_CONTAINER}" \
            >/dev/null 2>&1 || true
    fi


    success "Analytics stopped"
}


# ------------------------------------------------------------
# Spring Boot
# ------------------------------------------------------------

stop_spring() {
    section "Spring Boot"

    stop_managed_process \
        "Spring Boot" \
        "${SPRING_PID_FILE}"


    # Same safety rule as Angular: never kill an unknown process
    # just because it happens to own LEAP's normal port.
    if port_is_in_use "${SPRING_PORT}"; then
        warn "Port ${SPRING_PORT} is still in use"

        show_port_owner "${SPRING_PORT}"

        echo
        warn "The remaining process was not stopped because LEAP does not own it."
    fi
}


# ------------------------------------------------------------
# PostgreSQL
# ------------------------------------------------------------

stop_postgres() {
    section "PostgreSQL"

    if ! command_exists docker; then
        warn "Docker is unavailable; PostgreSQL container could not be checked"
        return 0
    fi


    if ! docker_is_running; then
        warn "Docker daemon is not running; PostgreSQL container could not be checked"
        return 0
    fi


    if ! docker_container_exists "${POSTGRES_CONTAINER}"; then
        info "LEAP PostgreSQL container does not exist"
        return 0
    fi


    if ! docker_container_running "${POSTGRES_CONTAINER}"; then
        info "PostgreSQL is already stopped"
        return 0
    fi


    info "Stopping ${POSTGRES_CONTAINER}"

    docker stop \
        --time 15 \
        "${POSTGRES_CONTAINER}" \
        >/dev/null


    success "PostgreSQL stopped"
    success "Database data was preserved"
}


# ------------------------------------------------------------
# Final summary
# ------------------------------------------------------------

print_stopped_summary() {
    section "LEAP stopped"

    success "LEAP shutdown complete"

    echo
    echo "Database data has NOT been deleted."
    echo
    echo "Start again with:"
    echo "  ./scripts/start.sh"
    echo
}


# ------------------------------------------------------------
# Main
# ------------------------------------------------------------

main() {
    print_leap_header
    ensure_run_dir

    # Reverse dependency order:
    #
    # Angular → Analytics → Spring → PostgreSQL

    stop_frontend
    stop_analytics
    stop_spring
    stop_postgres

    print_stopped_summary
}


main "$@"
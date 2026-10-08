#!/usr/bin/env bash

set -Eeuo pipefail

# ============================================================
# LEAP environment reset
#
# Default behavior:
#   - stop LEAP services
#   - clean stale LEAP runtime state
#   - preserve PostgreSQL data
#   - start LEAP again
#
# Usage:
#   ./scripts/reset.sh
#
# Optional destructive database reset:
#   ./scripts/reset.sh --database
# ============================================================


SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/common.sh"


RESET_DATABASE=false

POSTGRES_VOLUME="leap-postgres-data"


# ------------------------------------------------------------
# Arguments
# ------------------------------------------------------------

parse_arguments() {
    if [[ $# -eq 0 ]]; then
        return 0
    fi

    if [[ $# -eq 1 && "$1" == "--database" ]]; then
        RESET_DATABASE=true
        return 0
    fi

    echo "Usage:"
    echo "  ./scripts/reset.sh"
    echo "  ./scripts/reset.sh --database"
    exit 1
}


# ------------------------------------------------------------
# Stop current LEAP services
# ------------------------------------------------------------

stop_leap() {
    section "Stopping LEAP"

    "${SCRIPT_DIR}/stop.sh"
}


# ------------------------------------------------------------
# Runtime cleanup
# ------------------------------------------------------------

clean_runtime_state() {
    section "Cleaning LEAP runtime state"

    ensure_run_dir

    rm -f \
        "${RUN_DIR}/spring.pid" \
        "${RUN_DIR}/frontend.pid" \
        "${RUN_DIR}/spring.log" \
        "${RUN_DIR}/frontend.log"

    success "Removed LEAP PID and log files"


    if command_exists docker && docker_is_running; then

        if docker_container_exists "${ANALYTICS_CONTAINER}"; then
            info "Removing stale analytics container"

            docker rm -f \
                "${ANALYTICS_CONTAINER}" \
                >/dev/null 2>&1 || true

            success "Analytics container cleaned"
        else
            success "No stale analytics container found"
        fi

    else
        warn "Docker is not currently available; Docker cleanup was skipped"
    fi
}


# ------------------------------------------------------------
# Optional database reset
# ------------------------------------------------------------

confirm_database_reset() {
    echo
    echo "============================================================"
    echo " WARNING: DATABASE RESET"
    echo "============================================================"
    echo
    echo "This will permanently delete the LOCAL LEAP PostgreSQL data."
    echo
    echo "Flyway will recreate the schema when Spring starts again."
    echo
    echo "This does NOT affect Git or another teammate's database."
    echo

    read -r -p "Type RESET to continue: " confirmation

    if [[ "${confirmation}" != "RESET" ]]; then
        echo
        warn "Database reset cancelled."
        exit 0
    fi
}


reset_database() {
    section "Resetting local database"

    confirm_database_reset


    if ! command_exists docker; then
        die "Docker is required to reset the database."
    fi

    if ! docker_is_running; then
        die "Docker daemon is not running."
    fi


    if docker_container_exists "${POSTGRES_CONTAINER}"; then
        info "Removing ${POSTGRES_CONTAINER}"

        docker rm -f \
            "${POSTGRES_CONTAINER}" \
            >/dev/null

        success "PostgreSQL container removed"
    fi


    if docker volume inspect "${POSTGRES_VOLUME}" >/dev/null 2>&1; then
        info "Removing PostgreSQL data volume"

        docker volume rm \
            "${POSTGRES_VOLUME}" \
            >/dev/null

        success "PostgreSQL data deleted"
    else
        success "No LEAP PostgreSQL data volume found"
    fi


    echo
    warn "The local database has been erased."
    info "setup.sh will recreate PostgreSQL using the team configuration."


    "${SCRIPT_DIR}/setup.sh"
}


# ------------------------------------------------------------
# Restart
# ------------------------------------------------------------

restart_leap() {
    section "Restarting LEAP"

    "${SCRIPT_DIR}/start.sh"
}


# ------------------------------------------------------------
# Main
# ------------------------------------------------------------

main() {
    parse_arguments "$@"

    print_leap_header

    stop_leap
    clean_runtime_state


    if [[ "${RESET_DATABASE}" == "true" ]]; then
        reset_database
    else
        section "Database"

        success "PostgreSQL data was preserved"
    fi


    restart_leap
}


main "$@"
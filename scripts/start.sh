#!/usr/bin/env bash

set -Eeuo pipefail

# ============================================================
# LEAP application startup
#
# Starts the complete local development application in order:
#
#   PostgreSQL
#       ↓
#   Spring Boot / Flyway
#       ↓
#   Python Analytics
#       ↓
#   Angular Frontend
#
# Usage:
#   ./scripts/start.sh
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


# ------------------------------------------------------------
# Setup verification
# ------------------------------------------------------------

verify_setup() {
    section "Checking environment"

    require_command docker "Docker"
    require_command java "Java"
    require_command curl "curl"

    # Always use the Node version pinned in .nvmrc.
    load_project_node

    require_command node "Node.js"
    require_command npm "npm"

    if ! docker_is_running; then
        die "Docker daemon is not running."
    fi

    load_env
    validate_required_env

    if [[ ! -f "${SPRING_LOCAL_CONFIG}" ]]; then
        die "Missing Spring local configuration:

${SPRING_LOCAL_CONFIG}

Run:
  ./scripts/setup.sh"
    fi

    if [[ ! -d "${FRONTEND_DIR}/node_modules" ]]; then
        die "Frontend dependencies are not installed.

Run:
  ./scripts/setup.sh"
    fi

    if ! docker_container_exists "${POSTGRES_CONTAINER}"; then
        die "LEAP PostgreSQL container does not exist.

Run:
  ./scripts/setup.sh"
    fi

    success "Environment is prepared"
}


# ------------------------------------------------------------
# Managed background process helper
# ------------------------------------------------------------

start_background_process() {
    local working_directory="$1"
    local pid_file="$2"
    local log_file="$3"

    shift 3

    (
        cd "${working_directory}"

        if command_exists setsid; then
            nohup setsid "$@" \
                >"${log_file}" \
                2>&1 \
                </dev/null &
        else
            nohup "$@" \
                >"${log_file}" \
                2>&1 \
                </dev/null &
        fi

        echo $! > "${pid_file}"
    )
}


# ------------------------------------------------------------
# PostgreSQL
# ------------------------------------------------------------

start_postgres() {
    section "PostgreSQL"

    if docker_container_running "${POSTGRES_CONTAINER}"; then
        success "${POSTGRES_CONTAINER} is already running"
    else
        if port_is_in_use "${POSTGRES_PORT}"; then
            error "PostgreSQL cannot start because port ${POSTGRES_PORT} is already in use."

            show_port_owner "${POSTGRES_PORT}"

            die "The script will not stop an unrelated process automatically."
        fi

        info "Starting ${POSTGRES_CONTAINER}"

        docker start "${POSTGRES_CONTAINER}" >/dev/null

        success "PostgreSQL container started"
    fi


    info "Waiting for PostgreSQL"

    local attempts=0
    local max_attempts=30

    until docker exec "${POSTGRES_CONTAINER}" \
        pg_isready \
        -U "${DB_USERNAME}" \
        -d "${LEAP_DB_NAME}" \
        >/dev/null 2>&1
    do
        ((attempts += 1))

        if (( attempts >= max_attempts )); then
            die "PostgreSQL did not become ready."
        fi

        sleep 1
    done

    success "PostgreSQL is ready"


    local db_result

    db_result="$(
        docker exec \
            -e PGPASSWORD="${DB_PASSWORD}" \
            "${POSTGRES_CONTAINER}" \
            psql \
            -U "${DB_USERNAME}" \
            -d "${LEAP_DB_NAME}" \
            -tAc "SELECT 1;" \
            2>/dev/null || true
    )"

    if [[ "${db_result}" != "1" ]]; then
        die "PostgreSQL is running, but the credentials in .env could not connect.

The existing database container may have been created with
different credentials.

The script will NOT recreate or delete the database automatically."
    fi

    success "Database connection verified"
}


# ------------------------------------------------------------
# Spring Boot
# ------------------------------------------------------------

start_spring() {
    section "Spring Boot"

    if [[ -f "${SPRING_PID_FILE}" ]]; then
        local existing_pid
        existing_pid="$(read_pid_file "${SPRING_PID_FILE}")"

        if pid_is_running "${existing_pid}"; then

            if curl \
                --silent \
                --fail \
                --max-time 2 \
                "http://127.0.0.1:${SPRING_PORT}/api/auth/health" \
                >/dev/null 2>&1; then

                success "Spring Boot is already running"
                return 0
            fi

            die "A LEAP Spring process is still running with PID ${existing_pid},
but its health check is failing.

Check:
  ${SPRING_LOG}

Or reset the LEAP environment later with:
  ./scripts/reset.sh"
        fi

        warn "Removing stale Spring PID file"
        rm -f "${SPRING_PID_FILE}"
    fi


    if port_is_in_use "${SPRING_PORT}"; then
        error "Spring Boot cannot start because port ${SPRING_PORT} is already in use."

        show_port_owner "${SPRING_PORT}"

        echo
        echo "A common cause on the EC2 environment is Jenkins."
        echo
        echo "The script will NOT stop the process automatically."

        die "Free port ${SPRING_PORT}, then run ./scripts/start.sh again."
    fi


    info "Starting Spring Boot"

    : > "${SPRING_LOG}"

    start_background_process \
        "${SPRING_DIR}" \
        "${SPRING_PID_FILE}" \
        "${SPRING_LOG}" \
        bash ./mvnw spring-boot:run \
        -Dspring-boot.run.profiles=local


    info "Waiting for Spring Boot and Flyway"

    if ! wait_for_http \
        "http://127.0.0.1:${SPRING_PORT}/api/auth/health" \
        "Spring Boot" \
        120; then

        echo
        error "Spring Boot failed to start."

        echo
        echo "Last 40 lines of ${SPRING_LOG}:"
        echo "----------------------------------------"

        tail -n 40 "${SPRING_LOG}" 2>/dev/null || true

        echo "----------------------------------------"

        local spring_pid
        spring_pid="$(read_pid_file "${SPRING_PID_FILE}")"

        if [[ -n "${spring_pid}" ]] && pid_is_running "${spring_pid}"; then
            kill "${spring_pid}" 2>/dev/null || true
        fi

        rm -f "${SPRING_PID_FILE}"

        die "Fix the Spring startup error, then run ./scripts/start.sh again."
    fi

    success "Spring Boot is ready"
    success "Flyway startup completed"
}


# ------------------------------------------------------------
# Analytics service
# ------------------------------------------------------------

start_analytics() {
    section "Analytics"

    # Always build from the current analytics source.
    #
    # Docker's layer cache makes this quick when nothing changed,
    # while ensuring pulled/edited Python code cannot accidentally
    # run against an older local Docker image.
    info "Ensuring analytics Docker image matches current source"

    docker build \
        -t "${ANALYTICS_IMAGE}" \
        "${ANALYTICS_DIR}"

    success "Analytics Docker image is current"


    local current_image_id

    current_image_id="$(
        docker image inspect \
            --format='{{.Id}}' \
            "${ANALYTICS_IMAGE}"
    )"


    if docker_container_exists "${ANALYTICS_CONTAINER}"; then

        local container_image_id

        container_image_id="$(
            docker inspect \
                --format='{{.Image}}' \
                "${ANALYTICS_CONTAINER}" \
                2>/dev/null || true
        )"


        # If the source changed, docker build above created a new
        # image. A container created from the previous image must
        # therefore be replaced.
        if [[ "${container_image_id}" != "${current_image_id}" ]]; then
            warn "Existing analytics container uses an older image"
            info "Replacing analytics container with the current image"

            docker rm -f \
                "${ANALYTICS_CONTAINER}" \
                >/dev/null

        elif docker_container_running "${ANALYTICS_CONTAINER}"; then

            if curl \
                --silent \
                --fail \
                --max-time 2 \
                "http://127.0.0.1:${ANALYTICS_PORT}/health" \
                >/dev/null 2>&1; then

                success "Analytics service is already running with the current image"
                return 0
            fi

            warn "Existing LEAP analytics container is unhealthy"
            info "Removing unhealthy LEAP analytics container"

            docker rm -f \
                "${ANALYTICS_CONTAINER}" \
                >/dev/null

        else
            warn "Removing stopped LEAP analytics container"

            docker rm \
                "${ANALYTICS_CONTAINER}" \
                >/dev/null
        fi
    fi


    if port_is_in_use "${ANALYTICS_PORT}"; then
        error "Analytics cannot start because port ${ANALYTICS_PORT} is already in use."

        show_port_owner "${ANALYTICS_PORT}"

        die "The script will not stop an unrelated process automatically."
    fi


    info "Starting analytics Docker container"

    docker run \
        -d \
        --rm \
        --name "${ANALYTICS_CONTAINER}" \
        --network host \
        -e DB_USERNAME \
        -e DB_PASSWORD \
        -e DB_HOST=localhost \
        -e DB_PORT="${POSTGRES_PORT}" \
        -e DB_NAME="${LEAP_DB_NAME}" \
        "${ANALYTICS_IMAGE}" \
        >/dev/null


    info "Waiting for analytics health check"

    if ! wait_for_http \
        "http://127.0.0.1:${ANALYTICS_PORT}/health" \
        "Analytics" \
        60; then

        echo
        error "Analytics service failed to start."

        echo
        echo "Analytics container logs:"
        echo "----------------------------------------"

        docker logs \
            --tail 40 \
            "${ANALYTICS_CONTAINER}" \
            2>&1 || true

        echo "----------------------------------------"

        docker rm -f \
            "${ANALYTICS_CONTAINER}" \
            >/dev/null 2>&1 || true

        die "Fix the analytics startup error, then rerun ./scripts/start.sh."
    fi

    success "Analytics service is healthy"
}


# ------------------------------------------------------------
# Angular frontend
# ------------------------------------------------------------

start_frontend() {
    section "Angular Frontend"

    if [[ -f "${FRONTEND_PID_FILE}" ]]; then
        local existing_pid
        existing_pid="$(read_pid_file "${FRONTEND_PID_FILE}")"

        if pid_is_running "${existing_pid}"; then

            if curl \
                --silent \
                --fail \
                --max-time 2 \
                "http://127.0.0.1:${FRONTEND_PORT}" \
                >/dev/null 2>&1; then

                success "Angular frontend is already running"
                return 0
            fi

            die "A LEAP Angular process is still running with PID ${existing_pid},
but the frontend is not responding.

Check:
  ${FRONTEND_LOG}

Or reset the environment later with:
  ./scripts/reset.sh"
        fi

        warn "Removing stale frontend PID file"
        rm -f "${FRONTEND_PID_FILE}"
    fi


    if port_is_in_use "${FRONTEND_PORT}"; then
        error "Angular cannot start because port ${FRONTEND_PORT} is already in use."

        show_port_owner "${FRONTEND_PORT}"

        die "The script will not stop an unrelated process automatically."
    fi


    info "Starting Angular frontend"

    : > "${FRONTEND_LOG}"

    start_background_process \
        "${FRONTEND_DIR}" \
        "${FRONTEND_PID_FILE}" \
        "${FRONTEND_LOG}" \
        npm start -- \
        --host 0.0.0.0 \
        --port "${FRONTEND_PORT}"


    info "Waiting for Angular"

    if ! wait_for_http \
        "http://127.0.0.1:${FRONTEND_PORT}" \
        "Angular" \
        120; then

        echo
        error "Angular failed to start."

        echo
        echo "Last 40 lines of ${FRONTEND_LOG}:"
        echo "----------------------------------------"

        tail -n 40 "${FRONTEND_LOG}" 2>/dev/null || true

        echo "----------------------------------------"

        local frontend_pid
        frontend_pid="$(read_pid_file "${FRONTEND_PID_FILE}")"

        if [[ -n "${frontend_pid}" ]] && pid_is_running "${frontend_pid}"; then
            kill "${frontend_pid}" 2>/dev/null || true
        fi

        rm -f "${FRONTEND_PID_FILE}"

        die "Fix the frontend startup error, then rerun ./scripts/start.sh."
    fi

    success "Angular frontend is ready"
}


# ------------------------------------------------------------
# Final status
# ------------------------------------------------------------

print_started_summary() {
    section "LEAP is running"

    echo "PostgreSQL:"
    echo "  localhost:${POSTGRES_PORT}"
    echo
    echo "Spring Boot:"
    echo "  http://localhost:${SPRING_PORT}"
    echo
    echo "Analytics:"
    echo "  http://localhost:${ANALYTICS_PORT}"
    echo
    echo "Frontend:"
    echo "  http://localhost:${FRONTEND_PORT}"
    echo
    echo "Logs:"
    echo "  Spring:   ${SPRING_LOG}"
    echo "  Frontend: ${FRONTEND_LOG}"
    echo
    echo "Analytics logs:"
    echo "  docker logs ${ANALYTICS_CONTAINER}"
    echo
    echo "Check everything:"
    echo "  ./scripts/status.sh"
    echo
    echo "Stop everything:"
    echo "  ./scripts/stop.sh"
    echo
}


# ------------------------------------------------------------
# Main startup flow
# ------------------------------------------------------------

main() {
    print_leap_header
    ensure_run_dir

    verify_setup

    start_postgres
    start_spring
    start_analytics
    start_frontend

    print_started_summary
}


main "$@"
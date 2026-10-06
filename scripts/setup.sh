#!/usr/bin/env bash

set -Eeuo pipefail

# ============================================================
# LEAP one-time development environment setup
#
# Run this:
#   - after cloning the project for the first time
#   - after major environment/configuration changes
#
# Usage:
#   ./scripts/setup.sh
#
# This script standardizes:
#   - Java
#   - Node / npm
#   - Docker
#   - environment variables
#   - Spring local configuration
#   - PostgreSQL
#   - frontend dependencies
#   - Spring dependencies
#   - analytics Docker image
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/common.sh"


# ------------------------------------------------------------
# Team standards
# ------------------------------------------------------------

REQUIRED_JAVA_MAJOR=21

REQUIRED_NODE_VERSION="20.20.2"
NVM_VERSION="v0.40.8"

POSTGRES_IMAGE="postgres:16"
POSTGRES_DB_NAME="leaping_db"
POSTGRES_VOLUME="leap-postgres-data"


# ------------------------------------------------------------
# Project structure
# ------------------------------------------------------------

check_required_directories() {
    [[ -d "${SPRING_DIR}" ]] \
        || die "Spring project not found at ${SPRING_DIR}"

    [[ -d "${ANALYTICS_DIR}" ]] \
        || die "Analytics project not found at ${ANALYTICS_DIR}"

    [[ -d "${FRONTEND_DIR}" ]] \
        || die "Frontend project not found at ${FRONTEND_DIR}"

    [[ -f "${SPRING_DIR}/mvnw" ]] \
        || die "Maven wrapper not found at ${SPRING_DIR}/mvnw"

    [[ -f "${FRONTEND_DIR}/package.json" ]] \
        || die "Frontend package.json was not found."

    [[ -f "${FRONTEND_DIR}/package-lock.json" ]] \
        || die "Frontend package-lock.json was not found."

    [[ -f "${ANALYTICS_DIR}/Dockerfile" ]] \
        || die "Analytics Dockerfile was not found."

    [[ -f "${ANALYTICS_DIR}/requirements.txt" ]] \
        || die "Analytics requirements.txt was not found."

    [[ -f "${REPO_ROOT}/.nvmrc" ]] \
        || die "Missing ${REPO_ROOT}/.nvmrc"

    success "Project structure"
}


# ------------------------------------------------------------
# Java
# ------------------------------------------------------------

check_java_version() {
    local java_major

    java_major="$(
        java -version 2>&1 \
            | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' \
            | head -n 1
    )"

    if [[ "${java_major}" != "${REQUIRED_JAVA_MAJOR}" ]]; then
        die "Java ${REQUIRED_JAVA_MAJOR} is required.

Detected:
$(java -version 2>&1 | head -n 1)"
    fi

    success "Java ${REQUIRED_JAVA_MAJOR}"
}


# ------------------------------------------------------------
# Node / nvm
# ------------------------------------------------------------

load_nvm() {
    export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"

    if [[ ! -s "${NVM_DIR}/nvm.sh" ]]; then
        return 1
    fi

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

    return 0
}


install_nvm() {
    info "nvm is not installed"
    info "Installing nvm ${NVM_VERSION}"

    local installer
    installer="$(mktemp)"

    if ! curl \
        -fsSL \
        "https://raw.githubusercontent.com/nvm-sh/nvm/${NVM_VERSION}/install.sh" \
        -o "${installer}"; then

        rm -f "${installer}"
        die "Could not download the nvm installer."
    fi

    bash "${installer}"
    rm -f "${installer}"

    export NVM_DIR="$HOME/.nvm"

    if [[ ! -s "${NVM_DIR}/nvm.sh" ]]; then
        die "nvm installation did not complete successfully."
    fi

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

    success "nvm ${NVM_VERSION} installed"
}


ensure_node_version() {
    local desired_version

    desired_version="$(
        tr -d '[:space:]' < "${REPO_ROOT}/.nvmrc"
    )"

    if [[ "${desired_version}" != "${REQUIRED_NODE_VERSION}" ]]; then
        die ".nvmrc does not match the LEAP team Node version.

Expected:
  ${REQUIRED_NODE_VERSION}

Found:
  ${desired_version}"
    fi

    if ! load_nvm; then
        install_nvm
    fi

    if [[ "$(nvm version "${desired_version}")" == "N/A" ]]; then
        info "Installing Node ${desired_version}"
        nvm install "${desired_version}"
    fi

    info "Activating Node ${desired_version}"

    nvm use "${desired_version}" >/dev/null
    nvm alias default "${desired_version}" >/dev/null

    hash -r

    if [[ "$(node --version)" != "v${desired_version}" ]]; then
        die "Failed to activate Node ${desired_version}.

Detected:
$(node --version)"
    fi

    success "Node $(node --version)"
    success "npm $(npm --version)"
}


# ------------------------------------------------------------
# Spring local configuration
# ------------------------------------------------------------

prepare_spring_local_config() {
    if [[ -f "${SPRING_LOCAL_CONFIG}" ]]; then
        success "Spring local configuration already exists"
        return 0
    fi

    info "Creating standard Spring local configuration"

    cat > "${SPRING_LOCAL_CONFIG}" <<'EOF'
# ============================================================
# LEAP Local Development Configuration
#
# This file is local-only and should NOT be committed.
# Secrets come from the repository root .env file.
# ============================================================

server:
  port: 8081

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/leaping_db
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    database-platform: org.hibernate.dialect.PostgreSQLDialect

  flyway:
    enabled: true

jwt:
  secret: ${JWT_SECRET}
EOF

    success "Created application-local.yaml"
}


check_existing_spring_config() {
    if [[ ! -f "${SPRING_LOCAL_CONFIG}" ]]; then
        return 0
    fi

    local warning_found=false

    if ! grep -qE '^[[:space:]]*port:[[:space:]]*8081[[:space:]]*$' \
        "${SPRING_LOCAL_CONFIG}"; then

        warn "application-local.yaml does not use the standard LEAP Spring port: 8081"
        warning_found=true
    fi

    if ! grep -q \
        'jdbc:postgresql://localhost:5432/leaping_db' \
        "${SPRING_LOCAL_CONFIG}"; then

        warn "application-local.yaml does not appear to use the standard LEAP database URL:"
        echo "  jdbc:postgresql://localhost:5432/leaping_db"
        warning_found=true
    fi

    if ! grep -q 'DB_USERNAME' "${SPRING_LOCAL_CONFIG}"; then
        warn "application-local.yaml does not reference DB_USERNAME from .env"
        warning_found=true
    fi

    if ! grep -q 'DB_PASSWORD' "${SPRING_LOCAL_CONFIG}"; then
        warn "application-local.yaml does not reference DB_PASSWORD from .env"
        warning_found=true
    fi

    if ! grep -q 'JWT_SECRET' "${SPRING_LOCAL_CONFIG}"; then
        warn "application-local.yaml does not reference JWT_SECRET from .env"
        warning_found=true
    fi

    if [[ "${warning_found}" == "false" ]]; then
        success "Spring local configuration matches the team environment"
    else
        echo
        warn "The script will NOT overwrite an existing application-local.yaml."
        warn "Review this file before starting LEAP:"
        echo "  ${SPRING_LOCAL_CONFIG}"
    fi
}


# ------------------------------------------------------------
# Environment setup
# ------------------------------------------------------------

prepare_env_file() {
    local env_example="${REPO_ROOT}/.env.example"

    if [[ -f "${ENV_FILE}" ]]; then
        success ".env already exists"
        return 0
    fi

    if [[ ! -f "${env_example}" ]]; then
        die "Missing ${env_example}"
    fi

    info "Creating local .env from .env.example"

    cp "${env_example}" "${ENV_FILE}"

    success "Created .env"
}


# ------------------------------------------------------------
# PostgreSQL
# ------------------------------------------------------------

prepare_postgres() {
    if docker_container_exists "${POSTGRES_CONTAINER}"; then
        success "PostgreSQL container already exists: ${POSTGRES_CONTAINER}"

        local existing_image

        existing_image="$(
            docker inspect \
                --format='{{.Config.Image}}' \
                "${POSTGRES_CONTAINER}" \
                2>/dev/null || true
        )"

        if [[ "${existing_image}" != "${POSTGRES_IMAGE}" ]]; then
            warn "${POSTGRES_CONTAINER} uses image '${existing_image}', not '${POSTGRES_IMAGE}'."
            warn "The script will not automatically replace an existing database."
        fi

        return 0
    fi

    if port_is_in_use "${POSTGRES_PORT}"; then
        error "Cannot create LEAP PostgreSQL because port ${POSTGRES_PORT} is already in use."
        show_port_owner "${POSTGRES_PORT}"
        die "Free port ${POSTGRES_PORT}, then rerun setup."
    fi

    info "Creating LEAP PostgreSQL container"

    docker run \
        -d \
        --name "${POSTGRES_CONTAINER}" \
        -e POSTGRES_DB="${POSTGRES_DB_NAME}" \
        -e POSTGRES_USER="${DB_USERNAME}" \
        -e POSTGRES_PASSWORD="${DB_PASSWORD}" \
        -p "${POSTGRES_PORT}:5432" \
        -v "${POSTGRES_VOLUME}:/var/lib/postgresql/data" \
        "${POSTGRES_IMAGE}" \
        >/dev/null

    success "Created PostgreSQL container: ${POSTGRES_CONTAINER}"
}


start_and_verify_postgres() {
    if ! docker_container_running "${POSTGRES_CONTAINER}"; then
        if port_is_in_use "${POSTGRES_PORT}"; then
            error "PostgreSQL cannot start because port ${POSTGRES_PORT} is already in use."
            show_port_owner "${POSTGRES_PORT}"
            die "Free port ${POSTGRES_PORT}, then rerun setup."
        fi

        info "Starting PostgreSQL"
        docker start "${POSTGRES_CONTAINER}" >/dev/null
    fi

    info "Waiting for PostgreSQL"

    local attempts=0
    local max_attempts=30

    until docker exec "${POSTGRES_CONTAINER}" \
        pg_isready \
        -U "${DB_USERNAME}" \
        -d "${POSTGRES_DB_NAME}" \
        >/dev/null 2>&1
    do
        ((attempts += 1))

        if (( attempts >= max_attempts )); then
            die "PostgreSQL did not become ready."
        fi

        sleep 1
    done

    success "PostgreSQL is accepting connections"

    local db_result

    db_result="$(
        docker exec \
            -e PGPASSWORD="${DB_PASSWORD}" \
            "${POSTGRES_CONTAINER}" \
            psql \
            -U "${DB_USERNAME}" \
            -d "${POSTGRES_DB_NAME}" \
            -tAc "SELECT 1;" \
            2>/dev/null || true
    )"

    if [[ "${db_result}" != "1" ]]; then
        die "Could not connect to the LEAP database using the credentials in .env.

The existing ${POSTGRES_CONTAINER} container may have been created
with different credentials.

The script will NOT delete or recreate your database automatically."
    fi

    success "Database credentials verified"
}


# ------------------------------------------------------------
# Frontend
# ------------------------------------------------------------

prepare_frontend() {
    section "Frontend dependencies"

    info "Installing exact frontend dependencies with npm ci"

    (
        cd "${FRONTEND_DIR}"
        npm ci
    )

    success "Frontend dependencies installed"
}


# ------------------------------------------------------------
# Spring
# ------------------------------------------------------------

prepare_spring() {
    section "Spring dependencies"

    info "Compiling Spring project with the repository Maven Wrapper"

    (
        cd "${SPRING_DIR}"

        bash ./mvnw \
            -DskipTests \
            compile
    )

    success "Spring project compiled"
}


# ------------------------------------------------------------
# Analytics
# ------------------------------------------------------------

prepare_analytics() {
    section "Analytics environment"

    info "Building analytics Docker image"

    docker build \
        -t "${ANALYTICS_IMAGE}" \
        "${ANALYTICS_DIR}"

    success "Analytics Docker image built: ${ANALYTICS_IMAGE}"
}


# ------------------------------------------------------------
# Main setup flow
# ------------------------------------------------------------

main() {
    print_leap_header
    ensure_run_dir

    section "Checking project"

    check_required_directories

    section "Checking required tools"

    require_command git "Git"
    require_command docker "Docker"
    require_command java "Java"
    require_command curl "curl"

    success "Required base commands found"

    section "Checking runtime versions"

    check_java_version
    ensure_node_version

    require_command node "Node.js"
    require_command npm "npm"

    section "Checking Docker"

    if ! docker_is_running; then
        die "Docker is installed but the Docker daemon is not running."
    fi

    success "Docker daemon is running"

    section "Checking environment variables"

    prepare_env_file
    load_env
    validate_required_env

    success ".env found"
    success "DB_USERNAME is set"
    success "DB_PASSWORD is set"
    success "JWT_SECRET is set"

    section "Checking Spring local configuration"

    prepare_spring_local_config
    check_existing_spring_config

    section "Preparing PostgreSQL"

    prepare_postgres
    start_and_verify_postgres

    prepare_frontend
    prepare_spring
    prepare_analytics

    section "Setup complete"

    success "LEAP development environment is ready"

    echo
    echo "Prepared:"
    echo "  ✓ Java ${REQUIRED_JAVA_MAJOR}"
    echo "  ✓ Node ${REQUIRED_NODE_VERSION}"
    echo "  ✓ PostgreSQL ${POSTGRES_IMAGE}"
    echo "  ✓ Spring dependencies"
    echo "  ✓ Analytics Docker image"
    echo "  ✓ Frontend dependencies"

    echo
    echo "Next:"
    echo "  ./scripts/start.sh"
    echo
}


main "$@"

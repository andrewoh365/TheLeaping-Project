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

STANDARD_SPRING_DB_URL="jdbc:postgresql://localhost:${POSTGRES_PORT}/${POSTGRES_DB_NAME}"
STANDARD_SPRING_PORT="${SPRING_PORT}"


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

    [[ -f "${REPO_ROOT}/.env.example" ]] \
        || die "Missing ${REPO_ROOT}/.env.example"

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

SPRING_CONFIG_SERVER_PORT=""
SPRING_CONFIG_DB_URL=""
SPRING_CONFIG_DB_USERNAME=""
SPRING_CONFIG_DB_PASSWORD=""
SPRING_CONFIG_JWT_SECRET=""


yaml_path_value() {
    local file="$1"
    local wanted_path="$2"

    awk -v wanted_path="${wanted_path}" '
        function trim(value) {
            gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
            return value
        }

        function leading_spaces(value, match_text) {
            match(value, /^[[:space:]]*/)
            match_text = substr(value, RSTART, RLENGTH)
            gsub(/\t/, "    ", match_text)
            return length(match_text)
        }

        function current_path(leaf, result, i) {
            result = ""

            for (i = 1; i <= depth; i++) {
                if (result != "") {
                    result = result "."
                }

                result = result keys[i]
            }

            if (result != "") {
                result = result "."
            }

            return result leaf
        }

        /^[[:space:]]*#/ || /^[[:space:]]*$/ {
            next
        }

        {
            indent = leading_spaces($0)
            content = $0
            sub(/^[[:space:]]*/, "", content)

            colon = index(content, ":")

            if (colon == 0) {
                next
            }

            key = trim(substr(content, 1, colon - 1))
            value = trim(substr(content, colon + 1))

            while (depth > 0 && indent <= indents[depth]) {
                delete keys[depth]
                delete indents[depth]
                depth--
            }

            if (value == "" || value ~ /^#/) {
                depth++
                keys[depth] = key
                indents[depth] = indent
                next
            }

            if (current_path(key) == wanted_path) {
                print value
                exit
            }
        }
    ' "${file}"
}


normalize_yaml_scalar() {
    local value="$1"

    value="$(
        printf '%s' "${value}" \
            | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//'
    )"

    if [[ "${value}" =~ ^\"(.*)\"[[:space:]]*(#.*)?$ ]]; then
        printf '%s' "${BASH_REMATCH[1]}"
        return 0
    fi

    if [[ "${value}" =~ ^\'(.*)\'[[:space:]]*(#.*)?$ ]]; then
        printf '%s' "${BASH_REMATCH[1]}"
        return 0
    fi

    value="$(
        printf '%s' "${value}" \
            | sed -E 's/[[:space:]]+#.*$//; s/[[:space:]]+$//'
    )"

    printf '%s' "${value}"
}


looks_like_env_reference() {
    local value="$1"

    [[ \
        "${value}" =~ ^\$\{[A-Za-z_][A-Za-z0-9_]*\}$ \
        || "${value}" =~ ^\$[A-Za-z_][A-Za-z0-9_]*$ \
    ]]
}


is_expected_env_reference() {
    local value="$1"
    local variable_name="$2"

    local braced_reference="\${${variable_name}}"
    local simple_reference="\$${variable_name}"

    [[ \
        "${value}" == "${braced_reference}" \
        || "${value}" == "${simple_reference}" \
    ]]
}


is_placeholder_value() {
    local value="$1"
    local upper_value="${value^^}"

    [[ -z "${value}" ]] && return 0

    case "${upper_value}" in
        YOUR_*|CHANGE_ME*|CHANGEME*|REPLACE_ME*|REPLACE_WITH_*|TODO|TODO_*|PLACEHOLDER*)
            return 0
            ;;
    esac

    return 1
}


ensure_spring_local_config() {
    if [[ -f "${SPRING_LOCAL_CONFIG}" ]]; then
        success "Existing application-local.yaml found"
        return 0
    fi

    info "application-local.yaml does not exist"
    info "Creating the standard LEAP local Spring configuration"

    mkdir -p "$(dirname "${SPRING_LOCAL_CONFIG}")"

    cat > "${SPRING_LOCAL_CONFIG}" <<'YAML'
# ============================================================
# LEAP local Spring configuration
#
# This file is intentionally local and gitignored.
# Secrets are loaded from the repository root .env file.
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
YAML

    success "Created application-local.yaml"
}


read_spring_local_config() {
    SPRING_CONFIG_SERVER_PORT="$(
        normalize_yaml_scalar "$(
            yaml_path_value \
                "${SPRING_LOCAL_CONFIG}" \
                "server.port"
        )"
    )"

    SPRING_CONFIG_DB_URL="$(
        normalize_yaml_scalar "$(
            yaml_path_value \
                "${SPRING_LOCAL_CONFIG}" \
                "spring.datasource.url"
        )"
    )"

    SPRING_CONFIG_DB_USERNAME="$(
        normalize_yaml_scalar "$(
            yaml_path_value \
                "${SPRING_LOCAL_CONFIG}" \
                "spring.datasource.username"
        )"
    )"

    SPRING_CONFIG_DB_PASSWORD="$(
        normalize_yaml_scalar "$(
            yaml_path_value \
                "${SPRING_LOCAL_CONFIG}" \
                "spring.datasource.password"
        )"
    )"

    SPRING_CONFIG_JWT_SECRET="$(
        normalize_yaml_scalar "$(
            yaml_path_value \
                "${SPRING_LOCAL_CONFIG}" \
                "jwt.secret"
        )"
    )"
}


validate_spring_local_config() {
    read_spring_local_config

    local invalid=false

    if [[ "${SPRING_CONFIG_SERVER_PORT}" != "${STANDARD_SPRING_PORT}" ]]; then
        error "application-local.yaml must set server.port to ${STANDARD_SPRING_PORT}."
        invalid=true
    fi

    if [[ "${SPRING_CONFIG_DB_URL}" != "${STANDARD_SPRING_DB_URL}" ]]; then
        error "application-local.yaml must use this datasource URL:
  ${STANDARD_SPRING_DB_URL}"
        invalid=true
    fi

    if is_placeholder_value "${SPRING_CONFIG_DB_USERNAME}"; then
        error "application-local.yaml is missing a real spring.datasource.username."
        invalid=true

    elif looks_like_env_reference "${SPRING_CONFIG_DB_USERNAME}" \
        && ! is_expected_env_reference \
            "${SPRING_CONFIG_DB_USERNAME}" \
            "DB_USERNAME"; then

        error "spring.datasource.username may only reference DB_USERNAME."
        invalid=true
    fi

    if is_placeholder_value "${SPRING_CONFIG_DB_PASSWORD}"; then
        error "application-local.yaml is missing a real spring.datasource.password."
        invalid=true

    elif looks_like_env_reference "${SPRING_CONFIG_DB_PASSWORD}" \
        && ! is_expected_env_reference \
            "${SPRING_CONFIG_DB_PASSWORD}" \
            "DB_PASSWORD"; then

        error "spring.datasource.password may only reference DB_PASSWORD."
        invalid=true
    fi

    if is_placeholder_value "${SPRING_CONFIG_JWT_SECRET}"; then
        error "application-local.yaml is missing a real jwt.secret."
        invalid=true

    elif looks_like_env_reference "${SPRING_CONFIG_JWT_SECRET}" \
        && ! is_expected_env_reference \
            "${SPRING_CONFIG_JWT_SECRET}" \
            "JWT_SECRET"; then

        error "jwt.secret may only reference JWT_SECRET."
        invalid=true
    fi

    if [[ "${invalid}" == "true" ]]; then
        die "Spring local configuration does not match the LEAP team standard.

The setup script did NOT overwrite your existing file:
  ${SPRING_LOCAL_CONFIG}

Correct the values reported above, then rerun:
  ./scripts/setup.sh"
    fi

    success "Spring local configuration matches the team standard"
}


# ------------------------------------------------------------
# Environment setup
# ------------------------------------------------------------

read_env_example_value() {
    local variable_name="$1"
    local env_example="${REPO_ROOT}/.env.example"

    (
        set +u

        # shellcheck disable=SC1090
        source "${env_example}"

        printf '%s' "${!variable_name:-}"
    )
}


bootstrap_env_value() {
    local spring_value="$1"
    local variable_name="$2"

    if is_expected_env_reference \
        "${spring_value}" \
        "${variable_name}"; then

        read_env_example_value "${variable_name}"
        return 0
    fi

    printf '%s' "${spring_value}"
}


prepare_env_file() {
    if [[ -f "${ENV_FILE}" ]]; then
        success ".env already exists"
        return 0
    fi

    info ".env does not exist"
    info "Creating .env from the existing Spring configuration"

    local db_username
    local db_password
    local jwt_secret

    db_username="$(
        bootstrap_env_value \
            "${SPRING_CONFIG_DB_USERNAME}" \
            "DB_USERNAME"
    )"

    db_password="$(
        bootstrap_env_value \
            "${SPRING_CONFIG_DB_PASSWORD}" \
            "DB_PASSWORD"
    )"

    jwt_secret="$(
        bootstrap_env_value \
            "${SPRING_CONFIG_JWT_SECRET}" \
            "JWT_SECRET"
    )"

    if [[ \
        -z "${db_username}" \
        || -z "${db_password}" \
        || -z "${jwt_secret}" \
    ]]; then
        die "Could not create .env because one or more required values are missing."
    fi

    local old_umask
    old_umask="$(umask)"

    umask 077

    {
        echo "# LEAP local development environment"
        echo "# Generated by ./scripts/setup.sh"
        echo "# This file is gitignored."
        echo

        printf 'DB_USERNAME=%q\n' "${db_username}"
        printf 'DB_PASSWORD=%q\n' "${db_password}"
        printf 'JWT_SECRET=%q\n' "${jwt_secret}"
    } > "${ENV_FILE}"

    umask "${old_umask}"

    success "Created .env"
}


validate_spring_env_alignment() {
    local invalid=false

    if ! is_expected_env_reference \
        "${SPRING_CONFIG_DB_USERNAME}" \
        "DB_USERNAME" \
        && [[ "${SPRING_CONFIG_DB_USERNAME}" != "${DB_USERNAME}" ]]; then

        error "DB_USERNAME in .env does not match spring.datasource.username in application-local.yaml."

        echo "  Spring username: ${SPRING_CONFIG_DB_USERNAME}" >&2
        echo "  .env username:   ${DB_USERNAME}" >&2

        invalid=true
    fi

    if ! is_expected_env_reference \
        "${SPRING_CONFIG_DB_PASSWORD}" \
        "DB_PASSWORD" \
        && [[ "${SPRING_CONFIG_DB_PASSWORD}" != "${DB_PASSWORD}" ]]; then

        error "DB_PASSWORD in .env does not match spring.datasource.password in application-local.yaml."

        invalid=true
    fi

    if ! is_expected_env_reference \
        "${SPRING_CONFIG_JWT_SECRET}" \
        "JWT_SECRET" \
        && [[ "${SPRING_CONFIG_JWT_SECRET}" != "${JWT_SECRET}" ]]; then

        error "JWT_SECRET in .env does not match jwt.secret in application-local.yaml."

        invalid=true
    fi

    if [[ "${invalid}" == "true" ]]; then
        die "Spring and .env are using different local credentials/secrets.

The setup script will not overwrite either existing file automatically.

Update application-local.yaml or .env so they agree, then rerun:
  ./scripts/setup.sh"
    fi

    success "Spring and .env configuration agree"
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

    section "Checking Spring local configuration"

    ensure_spring_local_config
    validate_spring_local_config

    section "Checking environment variables"

    prepare_env_file
    load_env
    validate_required_env
    validate_spring_env_alignment

    success ".env found"
    success "DB_USERNAME is set"
    success "DB_PASSWORD is set"
    success "JWT_SECRET is set"

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
    echo "  ✓ Spring local configuration"
    echo "  ✓ Local .env"
    echo "  ✓ Spring dependencies"
    echo "  ✓ Analytics Docker image"
    echo "  ✓ Frontend dependencies"

    echo
    echo "Next:"
    echo "  ./scripts/start.sh"
    echo
}


main "$@"

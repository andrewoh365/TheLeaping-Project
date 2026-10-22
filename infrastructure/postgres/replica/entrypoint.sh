#!/usr/bin/env bash

set -Eeuo pipefail

: "${PRIMARY_HOST:?PRIMARY_HOST is required}"
: "${PRIMARY_PORT:?PRIMARY_PORT is required}"
: "${PRIMARY_DB_NAME:?PRIMARY_DB_NAME is required}"
: "${REPLICATION_USER:?REPLICATION_USER is required}"
: "${REPLICATION_PASSWORD:?REPLICATION_PASSWORD is required}"
: "${REPLICATION_SLOT:?REPLICATION_SLOT is required}"

DATA_ROOT="${DATA_ROOT:-/var/lib/postgresql/data}"
PGDATA="${PGDATA:-${DATA_ROOT}/pgdata}"
PASSFILE_DIR="${PASSFILE_DIR:-/run/postgresql}"
PASSFILE="${PASSFILE_DIR}/.pgpass"
POSTGRES_ENTRYPOINT="${POSTGRES_ENTRYPOINT:-/usr/local/bin/docker-entrypoint.sh}"

validate_identifier() {
    local value="$1"

    [[ "${value}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]
}

validate_identifier "${REPLICATION_USER}" \
    || { echo "Invalid replication user identifier" >&2; exit 1; }
validate_identifier "${REPLICATION_SLOT}" \
    || { echo "Invalid replication slot identifier" >&2; exit 1; }

run_as_postgres() {
    if [[ "$(id -u)" == "0" ]]; then
        gosu postgres "$@"
    else
        "$@"
    fi
}

write_runtime_passfile() {
    mkdir -p "${PASSFILE_DIR}"
    if [[ "$(id -u)" == "0" ]]; then
        chown postgres:postgres "${PASSFILE_DIR}"
    fi
    chmod 700 "${PASSFILE_DIR}"

    run_as_postgres env \
        "PASSFILE=${PASSFILE}" \
        "REPLICATION_USER=${REPLICATION_USER}" \
        "REPLICATION_PASSWORD=${REPLICATION_PASSWORD}" \
        sh -c 'printf "%s\n" "*:*:*:${REPLICATION_USER}:${REPLICATION_PASSWORD}" > "${PASSFILE}" && chmod 600 "${PASSFILE}"'
}

configure_standby_connection() {
    run_as_postgres sh -c "sed -i \
        -e '/^[[:space:]]*primary_conninfo[[:space:]]*=/d' \
        -e '/^[[:space:]]*primary_slot_name[[:space:]]*=/d' \
        -e '/^[[:space:]]*hot_standby[[:space:]]*=/d' \
        '${PGDATA}/postgresql.auto.conf'"

    run_as_postgres sh -c "printf '%s\n' \
        \"primary_conninfo = 'host=${PRIMARY_HOST} port=${PRIMARY_PORT} user=${REPLICATION_USER} passfile=${PASSFILE} application_name=leap-reporting-postgres'\" \
        \"primary_slot_name = '${REPLICATION_SLOT}'\" \
        'hot_standby = on' >> '${PGDATA}/postgresql.auto.conf'"
}

if [[ -f "${DATA_ROOT}/PG_VERSION" && ! -f "${PGDATA}/PG_VERSION" ]]; then
    echo "Reporting volume root contains an existing PostgreSQL cluster" >&2
    exit 1
fi

if [[ -f "${PGDATA}/PG_VERSION" ]]; then
    major_version="$(cat "${PGDATA}/PG_VERSION")"

    if [[ "${major_version}" != "16" ]]; then
        echo "Reporting data directory has PostgreSQL major version ${major_version}, expected 16" >&2
        exit 1
    fi

    if [[ ! -f "${PGDATA}/standby.signal" ]]; then
        echo "Reporting data directory is populated but is not a standby" >&2
        exit 1
    fi

    write_runtime_passfile
    rm -f "${DATA_ROOT}/.pgpass"
    configure_standby_connection
    exec "${POSTGRES_ENTRYPOINT}" postgres
fi

unexpected_root_entry="$(find "${DATA_ROOT}" \
    -mindepth 1 \
    -maxdepth 1 \
    ! -name lost+found \
    ! -name "$(basename "${PGDATA}")" \
    -print \
    -quit)"

if [[ -n "${unexpected_root_entry}" ]]; then
    echo "Reporting data directory is populated without a valid PostgreSQL cluster" >&2
    exit 1
fi

mkdir -p "${PGDATA}"
if [[ "$(id -u)" == "0" ]]; then
    chown postgres:postgres "${PGDATA}"
fi

write_runtime_passfile
rm -f "${DATA_ROOT}/.pgpass"

run_as_postgres env PGPASSFILE="${PASSFILE}" pg_basebackup \
    --pgdata="${PGDATA}" \
    --format=plain \
    --wal-method=stream \
    --progress \
    --write-recovery-conf \
    --slot="${REPLICATION_SLOT}" \
    --host="${PRIMARY_HOST}" \
    --port="${PRIMARY_PORT}" \
    --username="${REPLICATION_USER}" \
    --dbname="host=${PRIMARY_HOST} port=${PRIMARY_PORT} dbname=${PRIMARY_DB_NAME}" \
    --no-password

configure_standby_connection

if [[ "$(id -u)" == "0" ]]; then
    chown -R postgres:postgres "${PGDATA}"
fi

exec "${POSTGRES_ENTRYPOINT}" postgres

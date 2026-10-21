#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENTRYPOINT_SCRIPT="${SCRIPT_DIR}/entrypoint.sh"
TEST_ROOT="$(mktemp -d)"

cleanup() {
    rm -rf "${TEST_ROOT}"
}

trap cleanup EXIT

mkdir -p "${TEST_ROOT}/bin"
mkdir -p "${TEST_ROOT}/volume"

printf '%s\n' \
    '#!/usr/bin/env bash' \
    'set -Eeuo pipefail' \
    'for argument in "$@"; do' \
    '    if [[ "${argument}" == --pgdata=* ]]; then' \
    '        pgdata="${argument#--pgdata=}"' \
    '        mkdir -p "${pgdata}"' \
    '        printf '\''16\n'\'' > "${pgdata}/PG_VERSION"' \
    '        : > "${pgdata}/postgresql.auto.conf"' \
    '        : > "${pgdata}/standby.signal"' \
    '        exit 0' \
    '    fi' \
    'done' \
    'exit 1' > "${TEST_ROOT}/bin/pg_basebackup"

printf '%s\n' \
    '#!/usr/bin/env bash' \
    'set -Eeuo pipefail' \
    'exit 0' > "${TEST_ROOT}/bin/postgres-entrypoint"

chmod 700 \
    "${TEST_ROOT}/bin/pg_basebackup" \
    "${TEST_ROOT}/bin/postgres-entrypoint"

run_replica_entrypoint() {
    DATA_ROOT="${TEST_ROOT}/volume" \
    PASSFILE_DIR="${TEST_ROOT}/runtime" \
    POSTGRES_ENTRYPOINT="${TEST_ROOT}/bin/postgres-entrypoint" \
    PATH="${TEST_ROOT}/bin:${PATH}" \
    PRIMARY_HOST=primary \
    PRIMARY_PORT=5432 \
    PRIMARY_DB_NAME=leaping_db \
    REPLICATION_USER=leap_replication \
    REPLICATION_PASSWORD="$1" \
    REPLICATION_SLOT=leap_reporting_slot \
    bash "${ENTRYPOINT_SCRIPT}"
}

run_replica_entrypoint initial-secret

runtime_passfile="${TEST_ROOT}/runtime/.pgpass"
volume_passfile="${TEST_ROOT}/volume/.pgpass"
auto_conf="${TEST_ROOT}/volume/pgdata/postgresql.auto.conf"

[[ "$(stat -c '%a' "${runtime_passfile}")" == 600 ]]
grep -Fxq '*:*:*:leap_replication:initial-secret' "${runtime_passfile}"
[[ ! -e "${volume_passfile}" ]]
grep -Fq "passfile=${runtime_passfile}" "${auto_conf}"
! grep -Fq "${volume_passfile}" "${auto_conf}"

printf 'legacy-secret\n' > "${volume_passfile}"
run_replica_entrypoint rotated-secret

[[ "$(stat -c '%a' "${runtime_passfile}")" == 600 ]]
grep -Fxq '*:*:*:leap_replication:rotated-secret' "${runtime_passfile}"
[[ ! -e "${volume_passfile}" ]]
grep -Fq "passfile=${runtime_passfile}" "${auto_conf}"

printf 'Replica credential lifecycle checks passed.\n'
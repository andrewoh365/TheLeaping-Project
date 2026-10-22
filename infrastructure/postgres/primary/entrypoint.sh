#!/usr/bin/env bash

set -Eeuo pipefail

PGDATA="${PGDATA:-/var/lib/postgresql/data}"

if [[ -f "${PGDATA}/PG_VERSION" ]]; then
    /usr/local/bin/leap-replication-hba.sh
fi

exec /usr/local/bin/docker-entrypoint.sh "$@"
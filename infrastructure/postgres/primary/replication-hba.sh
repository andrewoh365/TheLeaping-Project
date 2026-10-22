#!/usr/bin/env bash

set -Eeuo pipefail

: "${REPLICATION_NETWORK_CIDR:?REPLICATION_NETWORK_CIDR is required}"
: "${REPLICATION_USER:?REPLICATION_USER is required}"

PGDATA="${PGDATA:-/var/lib/postgresql/data}"
hba_file="${PGDATA}/pg_hba.conf"
allow_rule="host replication ${REPLICATION_USER} ${REPLICATION_NETWORK_CIDR} scram-sha-256"
reject_ipv4="host replication ${REPLICATION_USER} 0.0.0.0/0 reject"
reject_ipv6="host replication ${REPLICATION_USER} ::/0 reject"

if grep -Fqx "${allow_rule}" "${hba_file}"; then
    exit 0
fi

if grep -Eq '^host[[:space:]]+replication[[:space:]]+[^[:space:]]+[[:space:]]+(all|0\.0\.0\.0/0|::/0)[[:space:]]' "${hba_file}"; then
    echo "A broad replication HBA rule already exists; refusing to widen or reorder it" >&2
    exit 1
fi

tmp_file="${hba_file}.leap.tmp"
awk \
    -v allow_rule="${allow_rule}" \
    -v reject_ipv4="${reject_ipv4}" \
    -v reject_ipv6="${reject_ipv6}" \
    'BEGIN { inserted = 0 }
     !inserted && $0 ~ /^host[[:space:]]+all[[:space:]]+all[[:space:]]+all/ {
         print allow_rule
         print reject_ipv4
         print reject_ipv6
         inserted = 1
     }
     { print }
     END {
         if (!inserted) {
             print allow_rule
             print reject_ipv4
             print reject_ipv6
         }
     }' "${hba_file}" > "${tmp_file}"

mv "${tmp_file}" "${hba_file}"
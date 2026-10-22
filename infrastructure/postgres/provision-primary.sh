#!/usr/bin/env bash

set -Eeuo pipefail

: "${PGHOST:?PGHOST is required}"
: "${PGPORT:?PGPORT is required}"
: "${PGDATABASE:?PGDATABASE is required}"
: "${PGUSER:?PGUSER is required}"
: "${PGPASSWORD:?PGPASSWORD is required}"
: "${REPLICATION_USER:?REPLICATION_USER is required}"
: "${REPLICATION_PASSWORD:?REPLICATION_PASSWORD is required}"
: "${REPORTING_DB_USERNAME:?REPORTING_DB_USERNAME is required}"
: "${REPORTING_DB_PASSWORD:?REPORTING_DB_PASSWORD is required}"
: "${REPLICATION_SLOT:?REPLICATION_SLOT is required}"

validate_identifier() {
    local value="$1"

    [[ "${value}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]
}

for identifier in \
    "${PGUSER}" \
    "${PGDATABASE}" \
    "${REPLICATION_USER}" \
    "${REPORTING_DB_USERNAME}" \
    "${REPLICATION_SLOT}"
do
    validate_identifier "${identifier}" \
        || { echo "Invalid PostgreSQL identifier" >&2; exit 1; }
done

psql_query() {
    psql -X -q -v ON_ERROR_STOP=1 -Atqc "$1"
}

psql_exec() {
    psql -X -q -v ON_ERROR_STOP=1 -c "$1" >/dev/null
}

current_wal_level="$(psql_query "SHOW wal_level")"
current_max_wal_senders="$(psql_query "SHOW max_wal_senders")"
current_max_replication_slots="$(psql_query "SHOW max_replication_slots")"
restart_required=false

if [[ "${current_wal_level}" != "replica" \
    && "${current_wal_level}" != "logical" ]]
then
    psql_exec "ALTER SYSTEM SET wal_level = 'replica'"
    restart_required=true
fi

if (( current_max_wal_senders < 10 )); then
    psql_exec "ALTER SYSTEM SET max_wal_senders = '10'"
    restart_required=true
fi

if (( current_max_replication_slots < 2 )); then
    psql_exec "ALTER SYSTEM SET max_replication_slots = '2'"
    restart_required=true
fi

psql_exec "ALTER SYSTEM SET max_slot_wal_keep_size = '2048MB'"

psql -X -q -v ON_ERROR_STOP=1 \
    -v replication_user="${REPLICATION_USER}" \
    -v replication_password="${REPLICATION_PASSWORD}" \
    -v reporting_user="${REPORTING_DB_USERNAME}" \
    -v reporting_password="${REPORTING_DB_PASSWORD}" \
    -v database_name="${PGDATABASE}" \
    -v slot_name="${REPLICATION_SLOT}" \
    <<'SQL' >/dev/null
SET password_encryption = 'scram-sha-256';

SELECT format(
    'CREATE ROLE %I LOGIN REPLICATION NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT',
    :'replication_user'
)
WHERE NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = :'replication_user'
)\gexec

SELECT format(
    'ALTER ROLE %I WITH LOGIN REPLICATION NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT PASSWORD %L',
    :'replication_user',
    :'replication_password'
)\gexec

SELECT format(
    'CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT NOREPLICATION',
    :'reporting_user'
)
WHERE NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = :'reporting_user'
)\gexec

SELECT format(
    'ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT NOREPLICATION PASSWORD %L',
    :'reporting_user',
    :'reporting_password'
)\gexec

SELECT format(
    'ALTER ROLE %I SET default_transaction_read_only = on',
    :'reporting_user'
)\gexec

SELECT format(
    'GRANT pg_read_all_stats TO %I',
    :'reporting_user'
)
\gexec

SELECT format(
    'GRANT CONNECT ON DATABASE %I TO %I',
    :'database_name',
    :'reporting_user'
)\gexec

SELECT format(
    'REVOKE pg_read_all_data FROM %I',
    :'reporting_user'
)\gexec

SELECT format(
    'REVOKE %I FROM %I',
    parent_role.rolname,
    member_role.rolname
)
FROM pg_auth_members AS membership
JOIN pg_roles AS parent_role
    ON parent_role.oid = membership.roleid
JOIN pg_roles AS member_role
    ON member_role.oid = membership.member
WHERE member_role.rolname = :'reporting_user'
  AND parent_role.rolname <> 'pg_read_all_stats'
\gexec

SELECT format(
    'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA %I '
    'REVOKE ALL ON TABLES FROM %I',
    grantor.rolname,
    schema_name.nspname,
    :'reporting_user'
)
FROM pg_default_acl AS default_acl
JOIN pg_roles AS grantor
    ON grantor.oid = default_acl.defaclrole
JOIN pg_namespace AS schema_name
    ON schema_name.oid = default_acl.defaclnamespace
WHERE default_acl.defaclobjtype = 'r'
  AND EXISTS (
      SELECT 1
      FROM aclexplode(default_acl.defaclacl) AS privilege
      WHERE privilege.grantee = (
          SELECT oid
          FROM pg_roles
          WHERE rolname = :'reporting_user'
      )
  )
\gexec

SELECT format(
    'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA %I '
    'REVOKE ALL ON TABLES FROM PUBLIC',
    grantor.rolname,
    schema_name.nspname
)
FROM pg_default_acl AS default_acl
JOIN pg_roles AS grantor
    ON grantor.oid = default_acl.defaclrole
JOIN pg_namespace AS schema_name
    ON schema_name.oid = default_acl.defaclnamespace
WHERE default_acl.defaclobjtype = 'r'
  AND EXISTS (
      SELECT 1
      FROM aclexplode(default_acl.defaclacl) AS privilege
      WHERE privilege.grantee = 0
  )
\gexec

SELECT format(
    'REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM %I',
    :'reporting_user'
)
\gexec

SELECT format(
    'REVOKE ALL PRIVILEGES ON SCHEMA public FROM %I',
    :'reporting_user'
)
\gexec

REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

SELECT format(
    'GRANT USAGE ON SCHEMA public TO %I',
    :'reporting_user'
)\gexec

SELECT format(
    'SELECT pg_create_physical_replication_slot(%L)',
    :'slot_name'
)
WHERE NOT EXISTS (
    SELECT 1
    FROM pg_replication_slots
    WHERE slot_name = :'slot_name'
)\gexec
SQL

required_reporting_tables_count() {
    psql_query "
        SELECT COUNT(*)
        FROM (VALUES
            ('portfolios'),
            ('trade_orders'),
            ('trades'),
            ('instruments'),
            ('markets')
        ) AS analytics_tables(table_name)
        WHERE to_regclass('public.' || table_name) IS NOT NULL
    "
}

table_wait_attempts=0
until [[ "$(required_reporting_tables_count)" == "5" ]]; do
    if (( table_wait_attempts >= 120 )); then
        echo "Required analytics tables were not created in time" >&2
        exit 1
    fi

    table_wait_attempts=$((table_wait_attempts + 1))
    sleep 1
done

psql -X -q -v ON_ERROR_STOP=1 \
    -v reporting_user="${REPORTING_DB_USERNAME}" \
    <<'SQL' >/dev/null
SELECT format(
    'GRANT SELECT ON TABLE %I TO %I',
    table_name,
    :'reporting_user'
)
FROM (VALUES
    ('portfolios'),
    ('trade_orders'),
    ('trades'),
    ('instruments'),
    ('markets')
) AS analytics_tables(table_name)\gexec
SQL

slot_type="$(psql_query \
    "SELECT slot_type FROM pg_replication_slots WHERE slot_name = '${REPLICATION_SLOT}'")"

if [[ "${slot_type}" != "physical" ]]; then
    echo "Replication slot is missing or is not physical" >&2
    exit 1
fi

psql_exec "SELECT pg_reload_conf()"

if [[ "${restart_required}" == "true" ]]; then
    echo "REPLICATION_PRIMARY_RESTART_REQUIRED"
    exit 10
fi

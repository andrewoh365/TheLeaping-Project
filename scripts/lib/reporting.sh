#!/usr/bin/env bash

# Shared reporting-primary/standby lifecycle helpers.
# This file is sourced by setup.sh, start.sh, stop.sh, status.sh, and reset.sh.

ensure_reporting_env() {
    require_env_var "REPLICATION_PASSWORD"
    require_env_var "REPORTING_DB_PASSWORD"
    require_env_var "ANALYTICS_INTERNAL_TOKEN"
}

reporting_volume_state() {
    docker run --rm -i \
        --entrypoint bash \
        -v "${REPORTING_VOLUME}:/data:ro" \
        postgres:16 \
        -s <<'VOLUME_CHECK'
set -eu

if [[ -f /data/pgdata/PG_VERSION ]]; then
    if [[ -f /data/pgdata/standby.signal ]]; then
        echo valid
    else
        echo invalid
    fi
    exit 0
fi

unexpected="$(find /data \
    -mindepth 1 \
    -maxdepth 1 \
    ! -name lost+found \
    -print \
    -quit)"

if [[ -n "${unexpected}" ]]; then
    echo invalid
else
    echo empty
fi
VOLUME_CHECK
}

ensure_reporting_network() {
    if docker network inspect "${REPORTING_NETWORK}" >/dev/null 2>&1; then
        local subnet
        subnet="$(docker network inspect \
            --format='{{(index .IPAM.Config 0).Subnet}}' \
            "${REPORTING_NETWORK}")"

        if [[ "${subnet}" != "${REPORTING_NETWORK_CIDR}" ]]; then
            die "${REPORTING_NETWORK} exists with subnet ${subnet}, expected ${REPORTING_NETWORK_CIDR}.

The network will not be deleted automatically. Inspect it and resolve the conflict before retrying."
        fi
    else
        docker network create \
            --driver bridge \
            --subnet "${REPORTING_NETWORK_CIDR}" \
            "${REPORTING_NETWORK}" \
            >/dev/null
    fi

    if ! docker inspect \
        --format='{{json .NetworkSettings.Networks}}' \
        "${POSTGRES_CONTAINER}" \
        | grep -q "${REPORTING_NETWORK}"; then
        docker network connect \
            --alias primary \
            "${REPORTING_NETWORK}" \
            "${POSTGRES_CONTAINER}"
    fi
}

# Docker Compose creates a network with the same name and labels it with its project.
reporting_network_owned_by_compose() {
    [[ -n "$(docker network inspect \
        --format='{{index .Labels "com.docker.compose.project"}}' \
        "${REPORTING_NETWORK}" 2>/dev/null)" ]]
}

# Removes the scripts' reporting network after stop.sh has stopped everything, so that
# Docker Compose can create its own network with the same name. start.sh recreates it.
remove_reporting_network() {
    if ! docker network inspect "${REPORTING_NETWORK}" >/dev/null 2>&1; then
        info "${REPORTING_NETWORK} does not exist"
        return 0
    fi

    if reporting_network_owned_by_compose; then
        info "${REPORTING_NETWORK} belongs to Docker Compose; leaving it in place"
        return 0
    fi

    if docker_container_exists "${POSTGRES_CONTAINER}"; then
        if docker_container_running "${POSTGRES_CONTAINER}"; then
            warn "${POSTGRES_CONTAINER} is still running; leaving ${REPORTING_NETWORK} in place"
            return 0
        fi

        # A container created on this network cannot start without it. setup.sh creates
        # the primary on Docker's default network, so this only protects other setups.
        if [[ "$(docker inspect --format='{{.HostConfig.NetworkMode}}' "${POSTGRES_CONTAINER}")" \
            == "${REPORTING_NETWORK}" ]]; then
            warn "${POSTGRES_CONTAINER} was created on ${REPORTING_NETWORK}; leaving it in place"
            return 0
        fi

        # The primary is kept between runs, so it is disconnected rather than removed.
        if [[ -n "$(docker inspect \
            --format="{{if index .NetworkSettings.Networks \"${REPORTING_NETWORK}\"}}yes{{end}}" \
            "${POSTGRES_CONTAINER}")" ]]; then
            docker network disconnect "${REPORTING_NETWORK}" "${POSTGRES_CONTAINER}" >/dev/null
        fi
    fi

    local attached
    attached="$(docker ps -a \
        --filter "network=${REPORTING_NETWORK}" \
        --format '{{.Names}}')"

    if [[ -n "${attached}" ]]; then
        warn "${REPORTING_NETWORK} is still used by: ${attached//$'\n'/, }; leaving it in place"
        return 0
    fi

    docker network rm "${REPORTING_NETWORK}" >/dev/null
    success "${REPORTING_NETWORK} removed"
}

ensure_local_primary_hba() {
    local allow_rule="host replication ${REPLICATION_USER} ${REPORTING_NETWORK_CIDR} scram-sha-256"
    local reject_ipv4="host replication ${REPLICATION_USER} 0.0.0.0/0 reject"
    local reject_ipv6="host replication ${REPLICATION_USER} ::/0 reject"

    docker exec -i \
        -e ALLOW_RULE="${allow_rule}" \
        -e REJECT_IPV4="${reject_ipv4}" \
        -e REJECT_IPV6="${reject_ipv6}" \
        "${POSTGRES_CONTAINER}" \
        sh -s <<'REMOTE_SCRIPT'
set -eu
hba_file=/var/lib/postgresql/data/pg_hba.conf

if grep -Fqx "${ALLOW_RULE}" "${hba_file}"; then
    exit 0
fi

if grep -Eq '^host[[:space:]]+replication[[:space:]]+[^[:space:]]+[[:space:]]+(all|0\.0\.0\.0/0|::/0)[[:space:]]' "${hba_file}"; then
    echo "A broad replication HBA rule already exists; refusing to widen or reorder it" >&2
    exit 1
fi

tmp_file="${hba_file}.leap.tmp"
awk \
    -v allow_rule="${ALLOW_RULE}" \
    -v reject_ipv4="${REJECT_IPV4}" \
    -v reject_ipv6="${REJECT_IPV6}" \
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
REMOTE_SCRIPT

    docker exec -u postgres -e PGPASSWORD="${DB_PASSWORD}" \
        "${POSTGRES_CONTAINER}" \
        psql \
        -U "${DB_USERNAME}" \
        -d "${LEAP_DB_NAME}" \
        -Atqc "SELECT pg_reload_conf()" \
        >/dev/null
}

local_primary_reporting_ip() {
    docker network inspect \
        --format="{{range .Containers}}{{if eq .Name \"${POSTGRES_CONTAINER}\"}}{{.IPv4Address}}{{end}}{{end}}" \
        "${REPORTING_NETWORK}" \
        | cut -d/ -f1
}

run_primary_provisioner() {
    local primary_ip
    primary_ip="$(local_primary_reporting_ip)"

    if [[ -z "${primary_ip}" ]]; then
        die "Could not resolve the primary PostgreSQL IP on ${REPORTING_NETWORK}."
    fi

    if docker volume inspect "${REPORTING_VOLUME}" >/dev/null 2>&1; then
        local volume_state
        volume_state="$(reporting_volume_state)"

        if [[ "${volume_state}" == "invalid" ]]; then
            die "${REPORTING_VOLUME} contains a partial or non-standby cluster.

The volume was not deleted or reinitialized automatically. Inspect it and explicitly remove/rebuild it before retrying."
        fi
    fi

    docker run \
        --rm \
        --network "${REPORTING_NETWORK}" \
        -e PGHOST="${primary_ip}" \
        -e PGPORT=5432 \
        -e PGDATABASE="${LEAP_DB_NAME}" \
        -e PGUSER="${DB_USERNAME}" \
        -e PGPASSWORD="${DB_PASSWORD}" \
        -e REPLICATION_USER="${REPLICATION_USER}" \
        -e REPLICATION_PASSWORD="${REPLICATION_PASSWORD}" \
        -e REPORTING_DB_USERNAME="${REPORTING_DB_USERNAME}" \
        -e REPORTING_DB_PASSWORD="${REPORTING_DB_PASSWORD}" \
        -e REPLICATION_SLOT="${REPLICATION_SLOT}" \
        -v "${REPO_ROOT}/infrastructure/postgres/provision-primary.sh:/provision-primary.sh:ro" \
        postgres:16 \
        bash /provision-primary.sh
}

provision_reporting_primary() {
    ensure_reporting_network
    ensure_local_primary_hba

    local status
    set +e
    run_primary_provisioner
    status=$?
    set -e

    if (( status == 10 )); then
        warn "Primary PostgreSQL restart is required for replication settings"
        info "Restarting only the LEAP primary container with its existing volume"
        docker restart "${POSTGRES_CONTAINER}" >/dev/null

        local attempts=0
        until docker exec "${POSTGRES_CONTAINER}" \
            pg_isready \
            -U "${DB_USERNAME}" \
            -d "${LEAP_DB_NAME}" \
            >/dev/null 2>&1
        do
            ((attempts += 1))
            if (( attempts >= 30 )); then
                die "Primary PostgreSQL did not become ready after the controlled replication restart."
            fi
            sleep 1
        done

        run_primary_provisioner
        return 0
    fi

    if (( status != 0 )); then
        die "Primary replication provisioning failed; no standby was started."
    fi
}

build_reporting_image() {
    docker build \
        -t "${REPORTING_IMAGE}" \
        "${REPO_ROOT}/infrastructure/postgres/replica"
}

reporting_recovery_state() {
    if [[ -z "${REPORTING_DB_PASSWORD:-}" ]]; then
        return 1
    fi

    docker exec \
        -e PGPASSWORD="${REPORTING_DB_PASSWORD}" \
        "${REPORTING_CONTAINER}" \
        psql \
        -U "${REPORTING_DB_USERNAME}" \
        -d "${LEAP_DB_NAME}" \
        -Atqc "SELECT pg_is_in_recovery()" 2>/dev/null || true
}

start_reporting_standby() {
    ensure_reporting_env
    provision_reporting_primary
    build_reporting_image

    local primary_ip
    primary_ip="$(local_primary_reporting_ip)"

    if [[ -z "${primary_ip}" ]]; then
        die "Could not resolve the primary PostgreSQL IP on ${REPORTING_NETWORK}."
    fi

    if docker_container_exists "${REPORTING_CONTAINER}"; then
        local current_image_id
        local container_image_id

        current_image_id="$(docker image inspect \
            --format='{{.Id}}' "${REPORTING_IMAGE}")"
        container_image_id="$(docker inspect \
            --format='{{.Image}}' "${REPORTING_CONTAINER}")"

        if [[ "${container_image_id}" != "${current_image_id}" ]]; then
            warn "${REPORTING_CONTAINER} uses an older image"
            docker rm -f "${REPORTING_CONTAINER}" >/dev/null
        fi
    fi

    if docker_container_exists "${REPORTING_CONTAINER}"; then
        if docker_container_running "${REPORTING_CONTAINER}"; then
            if [[ "$(reporting_recovery_state)" == "t" ]]; then
                success "${REPORTING_CONTAINER} is already running in recovery"
                return 0
            fi

            die "${REPORTING_CONTAINER} is running but is not a reporting standby."
        fi

        docker start "${REPORTING_CONTAINER}" >/dev/null
    else
        docker run \
            -d \
            --rm \
            --name "${REPORTING_CONTAINER}" \
            --network "${REPORTING_NETWORK}" \
            --network-alias reporting-postgres \
            -e POSTGRES_DB="${LEAP_DB_NAME}" \
            -e PRIMARY_HOST="${primary_ip}" \
            -e PRIMARY_PORT=5432 \
            -e PRIMARY_DB_NAME="${LEAP_DB_NAME}" \
            -e REPLICATION_USER="${REPLICATION_USER}" \
            -e REPLICATION_PASSWORD="${REPLICATION_PASSWORD}" \
            -e REPLICATION_SLOT="${REPLICATION_SLOT}" \
            -v "${REPORTING_VOLUME}:/var/lib/postgresql/data" \
            "${REPORTING_IMAGE}" \
            >/dev/null
    fi

    local attempts=0
    until [[ "$(reporting_recovery_state)" == "t" ]]; do
        ((attempts += 1))
        if (( attempts >= 60 )); then
            docker logs --tail 40 "${REPORTING_CONTAINER}" >&2 || true
            die "Reporting PostgreSQL did not become a read-only standby."
        fi
        sleep 1
    done

    success "${REPORTING_CONTAINER} is receiving as a read-only standby"
}

stop_reporting_standby() {
    if ! docker_container_exists "${REPORTING_CONTAINER}"; then
        return 0
    fi

    docker stop --time 15 "${REPORTING_CONTAINER}" >/dev/null 2>&1 || true
    if docker_container_exists "${REPORTING_CONTAINER}"; then
        docker rm -f "${REPORTING_CONTAINER}" >/dev/null 2>&1 || true
    fi
}

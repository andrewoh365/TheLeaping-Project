#!/usr/bin/env python3
"""Run and compare the isolated BR-16 analytics benchmark."""

from __future__ import annotations

import argparse
import json
import math
import os
import re
import socket
import subprocess
import sys
import threading
import time
from concurrent.futures import FIRST_COMPLETED, ThreadPoolExecutor, wait
from datetime import date, datetime, timedelta, timezone
from email.message import Message
from itertools import count
from pathlib import Path
from typing import Any, Callable, Iterable
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


TRADER_EMAILS = [
    "benchmark.trader01@leap.test",
    "benchmark.trader02@leap.test",
    "benchmark.trader03@leap.test",
    "benchmark.trader04@leap.test",
]
ANALYST_EMAIL = "benchmark.analyst@leap.test"
DEFAULT_TRADE_PASSWORD = "BenchmarkTrader123!"
DEFAULT_ANALYST_PASSWORD = "BenchmarkAnalyst123!"


class BenchmarkError(RuntimeError):
    pass


def env_float(name: str, default: float) -> float:
    value = float(os.getenv(name, str(default)))
    if value <= 0:
        raise BenchmarkError(f"{name} must be greater than zero")
    return value


def env_int(name: str, default: int) -> int:
    value = int(os.getenv(name, str(default)))
    if value <= 0:
        raise BenchmarkError(f"{name} must be greater than zero")
    return value


def percentile(values: list[float], percentile_value: float) -> float | None:
    """Calculate a linear-interpolated percentile from every completed call."""
    if not values:
        return None
    ordered = sorted(values)
    position = (len(ordered) - 1) * percentile_value / 100.0
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return round(ordered[lower], 3)
    fraction = position - lower
    result = ordered[lower] + (ordered[upper] - ordered[lower]) * fraction
    return round(result, 3)


def iso_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def parse_memory(value: str) -> float | None:
    """Convert Docker's human-readable memory value to MiB."""
    match = re.match(r"^\s*([0-9.]+)\s*([A-Za-z]+)", value)
    if match is None:
        return None
    number_value = float(match.group(1))

    unit = match.group(2).lower()
    multipliers = {
        "b": 1 / (1024 * 1024),
        "kib": 1 / 1024,
        "kb": 1 / 1024,
        "mib": 1,
        "mb": 1,
        "gib": 1024,
        "gb": 1000,
    }
    return round(number_value * multipliers.get(unit, 1), 3)


def parse_cpu(value: str) -> float | None:
    try:
        return float(value.rstrip("%"))
    except ValueError:
        return None


def run_command(command: list[str], timeout: float = 5.0) -> str:
    result = subprocess.run(
        command,
        check=True,
        capture_output=True,
        text=True,
        timeout=timeout,
    )
    return result.stdout.strip()


def request_raw(
    method: str,
    url: str,
    *,
    headers: dict[str, str] | None = None,
    body: bytes | None = None,
    timeout: float,
) -> tuple[dict[str, Any], bytes]:
    started = time.perf_counter()
    status: int | None = None
    response_body = b""
    error: str | None = None
    timed_out = False

    request = Request(url, data=body, headers=headers or {}, method=method)
    try:
        with urlopen(request, timeout=timeout) as response:
            status = response.status
            response_body = response.read()
    except HTTPError as exc:
        status = exc.code
        try:
            response_body = exc.read(4096)
        except OSError:
            response_body = b""
        error = f"HTTP {exc.code}"
    except (TimeoutError, socket.timeout) as exc:
        error = f"timeout: {exc}"
        timed_out = True
    except URLError as exc:
        reason = exc.reason
        error = f"network: {reason}"
        timed_out = isinstance(reason, (TimeoutError, socket.timeout))
    except OSError as exc:
        error = f"network: {exc}"

    record = {
        "latency_ms": round((time.perf_counter() - started) * 1000, 3),
        "status": status,
        "error": error,
        "timeout": timed_out,
    }
    return record, response_body


def request_json(
    method: str,
    url: str,
    *,
    headers: dict[str, str] | None = None,
    payload: dict[str, Any] | None = None,
    timeout: float,
) -> tuple[dict[str, Any], dict[str, Any] | None]:
    request_headers = {"Accept": "application/json"}
    request_headers.update(headers or {})
    body = None
    if payload is not None:
        request_headers["Content-Type"] = "application/json"
        body = json.dumps(payload).encode("utf-8")

    record, response_body = request_raw(
        method,
        url,
        headers=request_headers,
        body=body,
        timeout=timeout,
    )
    parsed: dict[str, Any] | None = None
    if response_body:
        try:
            parsed_value = json.loads(response_body.decode("utf-8"))
            if isinstance(parsed_value, dict):
                parsed = parsed_value
        except (UnicodeDecodeError, json.JSONDecodeError):
            parsed = None
    return record, parsed


def login(base_url: str, email: str, password: str, timeout: float) -> str:
    record, body = request_json(
        "POST",
        f"{base_url}/api/auth/login",
        payload={"email": email, "password": password},
        timeout=timeout,
    )
    if record["status"] != 200 or not body or not body.get("token"):
        raise BenchmarkError(
            f"Login failed for {email}: status={record['status']} error={record['error']}"
        )
    return str(body["token"])


def dashboard_queries(instrument_id: int) -> list[dict[str, str]]:
    today = date.today()
    start = today - timedelta(days=180)
    recent = today - timedelta(days=90)
    return [
        {
            "date_from": start.isoformat(),
            "date_to": today.isoformat(),
            "asset_type": "STOCK",
            "market": "NASDAQ",
            "client_segment": "FREQUENT",
        },
        {
            "date_from": start.isoformat(),
            "date_to": today.isoformat(),
            "asset_type": "CRYPTO",
            "market": "CRYPTO",
            "order_status": "FILLED",
            "client_segment": "REGULAR",
        },
        {
            "date_from": recent.isoformat(),
            "date_to": today.isoformat(),
            "market": "LSE",
            "order_status": "REJECTED",
            "client_segment": "OCCASIONAL",
        },
        {
            "date_from": start.isoformat(),
            "date_to": today.isoformat(),
            "instrument_id": str(instrument_id),
            "order_status": "FILLED",
            "client_segment": "FREQUENT",
        },
    ]


def summarize_records(
    records: list[dict[str, Any]],
    expected_status: int,
    duration_seconds: float,
) -> dict[str, Any]:
    latencies = [float(item["latency_ms"]) for item in records]
    successful = sum(item.get("status") == expected_status for item in records)
    timeouts = sum(bool(item.get("timeout")) for item in records)
    statuses: dict[str, int] = {}
    for item in records:
        key = str(item.get("status") or "network_error")
        statuses[key] = statuses.get(key, 0) + 1

    return {
        "requests": len(records),
        "successful": successful,
        "errors": len(records) - successful,
        "timeouts": timeouts,
        "status_counts": statuses,
        "throughput_per_second": round(successful / duration_seconds, 3),
        "latency_ms": {
            "min": round(min(latencies), 3) if latencies else None,
            "p50": percentile(latencies, 50),
            "p95": percentile(latencies, 95),
            "p99": percentile(latencies, 99),
            "max": round(max(latencies), 3) if latencies else None,
        },
        "latencies_ms": latencies,
    }


def combine_summaries(
    summaries: list[dict[str, Any]],
    duration_seconds: float,
) -> dict[str, Any]:
    latencies = [
        latency
        for summary in summaries
        for latency in summary["latencies_ms"]
    ]
    status_counts: dict[str, int] = {}
    for summary in summaries:
        for status, count_value in summary["status_counts"].items():
            status_counts[status] = status_counts.get(status, 0) + count_value

    successful = sum(summary["successful"] for summary in summaries)
    requests = sum(summary["requests"] for summary in summaries)
    timeouts = sum(summary["timeouts"] for summary in summaries)
    return {
        "requests": requests,
        "successful": successful,
        "errors": requests - successful,
        "timeouts": timeouts,
        "status_counts": status_counts,
        "throughput_per_second": round(successful / duration_seconds, 3),
        "latency_ms": {
            "min": round(min(latencies), 3) if latencies else None,
            "p50": percentile(latencies, 50),
            "p95": percentile(latencies, 95),
            "p99": percentile(latencies, 99),
            "max": round(max(latencies), 3) if latencies else None,
        },
        "latencies_ms": latencies,
    }


def run_stream(
    request_factory: Callable[[int], dict[str, Any]],
    target_rps: float,
    duration_seconds: float,
    workers: int,
) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    futures = set()
    interval = 1.0 / target_rps
    deadline = time.perf_counter() + duration_seconds
    next_launch = time.perf_counter()
    sequence = count()

    with ThreadPoolExecutor(max_workers=workers) as executor:
        while time.perf_counter() < deadline:
            while len(futures) >= workers:
                done, futures = wait(futures, return_when=FIRST_COMPLETED)
                for future in done:
                    records.append(future.result())

            futures.add(executor.submit(request_factory, next(sequence)))
            next_launch += interval
            delay = next_launch - time.perf_counter()
            if delay > 0:
                time.sleep(delay)

        if futures:
            done, _ = wait(futures)
            for future in done:
                records.append(future.result())

    return records


class MetricsSampler:
    def __init__(self, compose: list[str], db_password: str, reporting_password: str):
        self.compose = compose
        self.db_password = db_password
        self.reporting_password = reporting_password
        self.samples: list[dict[str, Any]] = []
        self.stop_event = threading.Event()
        self.thread: threading.Thread | None = None

    def psql(self, service: str, user: str, password: str, query: str) -> str:
        command = self.compose + [
            "exec",
            "-T",
            "-e",
            f"PGPASSWORD={password}",
            service,
            "psql",
            "-X",
            "-At",
            "-U",
            user,
            "-d",
            "leaping_db",
            "-c",
            query,
        ]
        return run_command(command)

    def database_metrics(self) -> dict[str, Any]:
        primary_connections = int(
            self.psql(
                "primary",
                os.environ["BENCHMARK_DB_USERNAME"],
                self.db_password,
                "SELECT count(*) FROM pg_stat_activity WHERE datname = 'leaping_db'",
            )
        )
        replica_connections = int(
            self.psql(
                "replica",
                os.environ["BENCHMARK_REPORTING_USER"],
                self.reporting_password,
                "SELECT count(*) FROM pg_stat_activity WHERE datname = 'leaping_db'",
            )
        )
        primary_replication = self.psql(
            "primary",
            os.environ["BENCHMARK_DB_USERNAME"],
            self.db_password,
            """
            SELECT
                count(*) || E'\\t' ||
                coalesce(max(CASE WHEN state = 'streaming' THEN 1 ELSE 0 END), 0)
                || E'\\t' ||
                coalesce(max(pg_wal_lsn_diff(sent_lsn, replay_lsn)), -1)
            FROM pg_stat_replication
            """,
        ).split("\t")
        replica_status = self.psql(
            "replica",
            os.environ["BENCHMARK_REPORTING_USER"],
            self.reporting_password,
            """
            SELECT
                pg_is_in_recovery() || E'\\t' ||
                coalesce(pg_last_wal_receive_lsn()::text, '') || E'\\t' ||
                coalesce(pg_last_wal_replay_lsn()::text, '') || E'\\t' ||
                coalesce(pg_wal_lsn_diff(
                    pg_last_wal_receive_lsn(), pg_last_wal_replay_lsn()
                ), -1)
            """,
        ).split("\t")

        return {
            "primary_connections": primary_connections,
            "replica_connections": replica_connections,
            "replication": {
                "primary_connections": int(primary_replication[0]),
                "streaming_connections": int(primary_replication[1]),
                "primary_replay_lag_bytes": float(primary_replication[2]),
                "replica_in_recovery": replica_status[0].lower() == "t",
                "received_lsn": replica_status[1] or None,
                "replayed_lsn": replica_status[2] or None,
                "replica_receive_replay_gap_bytes": float(replica_status[3]),
            },
        }

    def docker_metrics(self) -> dict[str, Any]:
        container_ids = run_command(
            [
                "docker",
                "ps",
                "-q",
                "--filter",
                f"label=com.docker.compose.project={os.environ['BENCHMARK_PROJECT']}",
            ]
        ).splitlines()
        if not container_ids:
            return {}

        output = run_command(
            [
                "docker",
                "stats",
                "--no-stream",
                "--format",
                "{{json .}}",
                *container_ids,
            ]
        )
        metrics: dict[str, Any] = {}
        for line in output.splitlines():
            try:
                item = json.loads(line)
            except json.JSONDecodeError:
                continue
            container_name = item.get("Name", "unknown")
            service = run_command(
                [
                    "docker",
                    "inspect",
                    "--format",
                    "{{index .Config.Labels \"com.docker.compose.service\"}}",
                    container_name,
                ]
            )
            memory_used, _, memory_limit = item.get("MemUsage", "").partition(" /")
            metrics[service] = {
                "cpu_percent": parse_cpu(item.get("CPUPerc", "")),
                "memory_used_mib": parse_memory(memory_used),
                "memory_limit_mib": parse_memory(memory_limit),
                "memory_percent": parse_cpu(item.get("MemPerc", "")),
            }
        return metrics

    def collect(self) -> None:
        try:
            self.samples.append(
                {
                    "timestamp": iso_now(),
                    "containers": self.docker_metrics(),
                    "database": self.database_metrics(),
                }
            )
        except (BenchmarkError, KeyError, OSError, ValueError, subprocess.SubprocessError) as exc:
            self.samples.append(
                {"timestamp": iso_now(), "error": str(exc)}
            )

    def loop(self) -> None:
        interval = float(os.getenv("BENCHMARK_METRIC_INTERVAL_SECONDS", "1"))
        while not self.stop_event.is_set():
            self.collect()
            self.stop_event.wait(interval)

    def start(self) -> None:
        self.stop_event.clear()
        self.thread = threading.Thread(target=self.loop, daemon=True)
        self.thread.start()

    def stop(self) -> None:
        self.stop_event.set()
        if self.thread is not None:
            self.thread.join(timeout=10)
        self.collect()


def metric_summary(samples: list[dict[str, Any]]) -> dict[str, Any]:
    valid = [item for item in samples if "error" not in item]
    result: dict[str, Any] = {
        "samples": len(samples),
        "valid_samples": len(valid),
        "container_resources": {},
        "database_connections": {},
        "replication": {},
    }

    services = set()
    for item in valid:
        services.update(item.get("containers", {}).keys())
    for service in sorted(services):
        service_items = [
            item["containers"][service]
            for item in valid
            if service in item.get("containers", {})
        ]
        cpus = [item["cpu_percent"] for item in service_items if item.get("cpu_percent") is not None]
        memory = [item["memory_used_mib"] for item in service_items if item.get("memory_used_mib") is not None]
        result["container_resources"][service] = {
            "cpu_percent_avg": round(sum(cpus) / len(cpus), 3) if cpus else None,
            "cpu_percent_max": round(max(cpus), 3) if cpus else None,
            "memory_used_mib_max": round(max(memory), 3) if memory else None,
        }

    for key in ("primary_connections", "replica_connections"):
        values = [
            item["database"].get(key)
            for item in valid
            if key in item.get("database", {})
        ]
        result["database_connections"][key] = max(values) if values else None

    lags = [
        item["database"]["replication"]["replica_receive_replay_gap_bytes"]
        for item in valid
        if "replication" in item.get("database", {})
    ]
    statuses = [
        item["database"]["replication"]
        for item in valid
        if "replication" in item.get("database", {})
    ]
    result["replication"] = {
        "streaming_samples": sum(item.get("streaming_connections", 0) > 0 for item in statuses),
        "max_receive_replay_gap_bytes": max(lags) if lags else None,
        "last": statuses[-1] if statuses else None,
    }
    return result


def query_counts(compose: list[str]) -> dict[str, int]:
    password = os.environ["BENCHMARK_DB_PASSWORD"]
    user = os.environ["BENCHMARK_DB_USERNAME"]
    query = "SELECT (SELECT count(*) FROM trade_orders) || E'\\t' || (SELECT count(*) FROM trades)"
    output = run_command(
        compose
        + [
            "exec",
            "-T",
            "-e",
            f"PGPASSWORD={password}",
            "primary",
            "psql",
            "-X",
            "-At",
            "-U",
            user,
            "-d",
            "leaping_db",
            "-c",
            query,
        ]
    )
    order_count, trade_count = output.split("\t")
    return {"trade_orders": int(order_count), "trades": int(trade_count)}


def wait_for_reporting(base_url: str, token: str, timeout: float) -> dict[str, Any]:
    deadline = time.perf_counter() + 30
    last_body: dict[str, Any] | None = None
    while time.perf_counter() < deadline:
        record, body = request_json(
            "GET",
            f"{base_url}/ready",
            headers={"X-Analytics-Internal-Token": token},
            timeout=timeout,
        )
        if record["status"] == 200 and body:
            last_body = body
            reporting = body.get("reporting", {})
            if (
                reporting.get("in_recovery") is True
                and reporting.get("receiver_status") == "streaming"
                and reporting.get("replay_pending") is False
            ):
                return body
        time.sleep(1)
    raise BenchmarkError(f"Reporting standby did not catch up: {last_body}")


def run_scenario(args: argparse.Namespace) -> dict[str, Any]:
    scenario = args.scenario
    base_url = os.environ.get("BENCHMARK_SPRING_URL", f"http://127.0.0.1:{os.environ.get('BENCHMARK_SPRING_PORT', '18080')}")
    analytics_url = os.environ.get("BENCHMARK_ANALYTICS_URL", f"http://127.0.0.1:{os.environ.get('BENCHMARK_ANALYTICS_PORT', '18000')}")
    timeout = env_float("BENCHMARK_HTTP_TIMEOUT_SECONDS", 35)
    samples = env_int("BENCHMARK_SAMPLES", 3)
    sample_seconds = env_float("BENCHMARK_SAMPLE_SECONDS", 10)
    trade_rps = env_float("BENCHMARK_TRADE_RPS", 2)
    report_rps = env_float("BENCHMARK_REPORT_RPS", 0.25)
    trade_workers = env_int("BENCHMARK_TRADE_WORKERS", 2)
    report_workers = env_int("BENCHMARK_REPORT_WORKERS", 1)
    warmup_orders = env_int("BENCHMARK_WARMUP_ORDERS", 4)
    warmup_reports = env_int("BENCHMARK_WARMUP_REPORTS", 2)
    instrument_id = int(os.environ["BENCHMARK_BTC_INSTRUMENT_ID"])

    if samples > 10 or sample_seconds > 120 or trade_rps > 5 or report_rps > 2:
        raise BenchmarkError(
            "Requested load exceeds the built-in modest safety caps: "
            "samples<=10, sample_seconds<=120, trade_rps<=5, report_rps<=2"
        )

    compose = [
        "docker-compose",
        "-p",
        os.environ["BENCHMARK_PROJECT"],
        "-f",
        os.environ["BENCHMARK_COMPOSE_FILE"],
    ]
    internal_token = os.environ["BENCHMARK_ANALYTICS_TOKEN"]
    analyst_token = login(
        base_url,
        ANALYST_EMAIL,
        os.environ.get("BENCHMARK_ANALYST_PASSWORD", DEFAULT_ANALYST_PASSWORD),
        timeout,
    )
    trader_tokens = [
        login(
            base_url,
            email,
            os.environ.get("BENCHMARK_TRADER_PASSWORD", DEFAULT_TRADE_PASSWORD),
            timeout,
        )
        for email in TRADER_EMAILS
    ]

    ready_record, ready_body = request_json(
        "GET",
        f"{analytics_url}/ready",
        headers={"X-Analytics-Internal-Token": internal_token},
        timeout=timeout,
    )
    if ready_record["status"] != 200 or not ready_body:
        raise BenchmarkError(f"FastAPI readiness failed: {ready_record}")
    wait_for_reporting(analytics_url, internal_token, timeout)

    def make_trade_request(sequence: int) -> dict[str, Any]:
        record, _ = request_json(
            "POST",
            f"{base_url}/api/trade/orders",
            headers={"Authorization": f"Bearer {trader_tokens[sequence % len(trader_tokens)]}"},
            payload={
                "instrument_id": instrument_id,
                "order_action": "BUY",
                "order_type": "MARKET",
                "quantity": "0.001",
                "time_in_force": "DAY",
            },
            timeout=timeout,
        )
        return record

    queries = dashboard_queries(instrument_id)

    def make_report_request(sequence: int) -> dict[str, Any]:
        query = urlencode(queries[sequence % len(queries)])
        record, _ = request_json(
            "GET",
            f"{base_url}/api/analytics/dashboard?{query}",
            headers={"Authorization": f"Bearer {analyst_token}"},
            timeout=timeout,
        )
        return record

    warmup_trade_records = [make_trade_request(index) for index in range(warmup_orders)]
    if any(item.get("status") != 201 for item in warmup_trade_records):
        raise BenchmarkError(f"Trading warmup failed: {warmup_trade_records}")

    if scenario == "with_analytics":
        warmup_report_records = [make_report_request(index) for index in range(warmup_reports)]
        if any(item.get("status") != 200 for item in warmup_report_records):
            raise BenchmarkError(f"Reporting warmup failed: {warmup_report_records}")

    wait_for_reporting(analytics_url, internal_token, timeout)
    before_counts = query_counts(compose)
    sample_results = []

    for sample_number in range(1, samples + 1):
        sampler = MetricsSampler(
            compose,
            os.environ["BENCHMARK_DB_PASSWORD"],
            os.environ["BENCHMARK_REPORTING_PASSWORD"],
        )
        sampler.start()
        started = time.perf_counter()
        if scenario == "with_analytics":
            with ThreadPoolExecutor(max_workers=2) as executor:
                trade_future = executor.submit(
                    run_stream,
                    make_trade_request,
                    trade_rps,
                    sample_seconds,
                    trade_workers,
                )
                report_future = executor.submit(
                    run_stream,
                    make_report_request,
                    report_rps,
                    sample_seconds,
                    report_workers,
                )
                trade_records = trade_future.result()
                report_records = report_future.result()
        else:
            trade_records = run_stream(
                make_trade_request,
                trade_rps,
                sample_seconds,
                trade_workers,
            )
            report_records = []
        elapsed = time.perf_counter() - started
        sampler.stop()

        sample_results.append(
            {
                "sample": sample_number,
                "elapsed_seconds": round(elapsed, 3),
                "trading": summarize_records(trade_records, 201, elapsed),
                "reporting": summarize_records(report_records, 200, elapsed),
                "metrics": metric_summary(sampler.samples),
                "raw_metrics": sampler.samples,
            }
        )

    after_counts = query_counts(compose)
    total_elapsed = sum(item["elapsed_seconds"] for item in sample_results)
    aggregate = {
        "trading": combine_summaries(
            [sample["trading"] for sample in sample_results],
            total_elapsed,
        ),
        "reporting": combine_summaries(
            [sample["reporting"] for sample in sample_results],
            total_elapsed,
        ),
    }

    result = {
        "schema_version": 1,
        "scenario": scenario,
        "timestamp_utc": iso_now(),
        "configuration": {
            "samples": samples,
            "sample_seconds": sample_seconds,
            "trade_rps": trade_rps,
            "report_rps": report_rps if scenario == "with_analytics" else 0,
            "trade_workers": trade_workers,
            "report_workers": report_workers if scenario == "with_analytics" else 0,
            "warmup_orders": warmup_orders,
            "warmup_reports": warmup_reports if scenario == "with_analytics" else 0,
            "instrument_id": instrument_id,
            "instrument_symbol": "BTC-USD",
            "spring_url": base_url,
            "analytics_url": analytics_url,
        },
        "dataset_counts": {"before": before_counts, "after": after_counts},
        "readiness": ready_body,
        "samples": sample_results,
        "aggregate": aggregate,
    }
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(result, indent=2) + "\n", encoding="ascii")
    print(json.dumps({"scenario": scenario, "output": str(output_path), "aggregate": aggregate}, indent=2))
    return result


def compare_results(baseline_path: str, analytics_path: str, output_path: str | None) -> int:
    baseline = json.loads(Path(baseline_path).read_text(encoding="ascii"))
    analytics = json.loads(Path(analytics_path).read_text(encoding="ascii"))
    baseline_trading = baseline["aggregate"]["trading"]
    analytics_trading = analytics["aggregate"]["trading"]
    baseline_p95 = baseline_trading["latency_ms"]["p95"]
    analytics_p95 = analytics_trading["latency_ms"]["p95"]
    if baseline_p95 in (None, 0) or analytics_p95 is None:
        raise BenchmarkError("Cannot compare p95 because one scenario has no completed trading calls")

    percentage_change = ((analytics_p95 - baseline_p95) / baseline_p95) * 100
    comparison = {
        "baseline": {
            "trading_p50_ms": baseline_trading["latency_ms"]["p50"],
            "trading_p95_ms": baseline_p95,
            "trading_throughput_per_second": baseline_trading["throughput_per_second"],
            "trading_errors": baseline_trading["errors"],
            "trading_timeouts": baseline_trading["timeouts"],
        },
        "with_analytics": {
            "trading_p50_ms": analytics_trading["latency_ms"]["p50"],
            "trading_p95_ms": analytics_p95,
            "trading_throughput_per_second": analytics_trading["throughput_per_second"],
            "trading_errors": analytics_trading["errors"],
            "trading_timeouts": analytics_trading["timeouts"],
            "reporting_p50_ms": analytics["aggregate"]["reporting"]["latency_ms"]["p50"],
            "reporting_p95_ms": analytics["aggregate"]["reporting"]["latency_ms"]["p95"],
            "reporting_errors": analytics["aggregate"]["reporting"]["errors"],
        },
        "trading_p95_percentage_change": round(percentage_change, 3),
        "acceptance_target": {
            "max_degradation_percentage": 15,
            "no_additional_trading_errors": True,
        },
        "pass": percentage_change <= 15 and analytics_trading["errors"] <= baseline_trading["errors"],
    }
    if output_path:
        Path(output_path).write_text(json.dumps(comparison, indent=2) + "\n", encoding="ascii")
    print(json.dumps(comparison, indent=2))
    return 0 if comparison["pass"] else 2


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    subparsers = result.add_subparsers(dest="command", required=True)
    run_parser = subparsers.add_parser("run")
    run_parser.add_argument("--scenario", choices=("baseline", "with_analytics"), required=True)
    run_parser.add_argument("--output", required=True)
    compare_parser = subparsers.add_parser("compare")
    compare_parser.add_argument("baseline")
    compare_parser.add_argument("with_analytics")
    compare_parser.add_argument("--output")
    return result


def main() -> int:
    args = parser().parse_args()
    try:
        if args.command == "run":
            run_scenario(args)
            return 0
        return compare_results(args.baseline, args.with_analytics, args.output)
    except (BenchmarkError, OSError, subprocess.SubprocessError, ValueError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

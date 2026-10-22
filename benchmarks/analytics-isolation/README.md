# Analytics isolation benchmark

This benchmark measures the real Spring Boot order workflow with and without
concurrent reporting requests. It is intentionally separate from the normal
LEAP environment:

- `docker-compose -p leap-analytics-benchmark` owns a unique network and two
  named database volumes.
- The primary and reporting standby are created from the repository's existing
  PostgreSQL scripts.
- Spring Boot and FastAPI run as disposable benchmark instances.
- No host PostgreSQL port is published. Spring and FastAPI use high, local-only
  ports for the runner.
- The SQL fixture creates only benchmark accounts in the disposable database.
- The runner removes only the Compose project it started, including only its
  own volumes. It refuses to run if that namespace already exists.

The Angular container is not included because the BR-16 measurement path is
Spring Boot order placement and the authenticated Spring Boot analytics proxy.
The request path is still end to end for both measured APIs:

```text
benchmark runner -> Spring Boot -> primary PostgreSQL
                 -> Spring Boot -> FastAPI -> reporting PostgreSQL standby
```

## Safe preflight

Run the no-start check first:

```bash
./benchmarks/analytics-isolation/run_benchmark.sh --check
```

The script requires Docker, `docker-compose` v2, Python 3, and `curl`. It
checks the Compose file, namespace, ports, Docker daemon, CPU count, and
available memory before starting anything. Runtime caps are 0.50 CPU / 768 MiB
for primary PostgreSQL, 0.25 CPU / 512 MiB for the standby, 0.50 CPU / 1 GiB
for Spring Boot, and 0.25 CPU / 512 MiB for FastAPI.

On the current EC2 host, the observed capacity is 2 vCPUs and 7.6 GiB RAM,
with roughly 2.1 GiB available and no swap while the development environment is
running. The default 3 GiB available-memory guard therefore refuses to start;
this is intentional. Do not lower the guard while the normal application is
running unless the host has been independently checked and the risk is
accepted.

## Run

After the preflight passes on a suitably provisioned host:

```bash
./benchmarks/analytics-isolation/run_benchmark.sh
```

The command runs two fresh stacks in sequence and writes JSON results under
`benchmarks/analytics-isolation/results/` (the directory is gitignored only if
the operator adds it locally; do not commit result files). Each scenario uses
the same migrations, fixture, sample count, duration, order stream, and
reporting query list. The default workload is deliberately modest:

- 4 disposable customer accounts with USD cash
- 4 warmup orders
- 3 measured samples of 10 seconds each
- 2 trading requests per second with two in-flight workers
- 0.25 reporting requests per second in the analytics scenario
- a 30-second database statement timeout and bounded analytics pool

Configuration is environment-based and capped by the Python runner. For
example:

```bash
BENCHMARK_SAMPLES=5 \
BENCHMARK_SAMPLE_SECONDS=20 \
BENCHMARK_TRADE_RPS=2 \
BENCHMARK_REPORT_RPS=0.25 \
BENCHMARK_OUTPUT_DIR=/tmp/leap-analytics-results \
./benchmarks/analytics-isolation/run_benchmark.sh
```

Keep the same values for both scenarios. The runner records raw measured
latencies, status counts, actual p50/p95/p99 calculations, successful order
throughput, errors and timeouts, reporting latency, PostgreSQL connection
counts, Docker CPU/memory samples, and primary/standby replication status and
lag. `comparison.json` calculates:

```text
((p95_with_analytics - p95_baseline) / p95_baseline) * 100
```

The project-defined acceptance criterion is a measured p95 degradation of at
most 15% and no more trading errors in the analytics scenario than in baseline.
This is not a BRS threshold. The criterion is a target for an executed run,
not evidence supplied by this repository; a non-zero exit from the comparison
means the observed result did not meet the target. Inspect both scenario JSON
files for the cause.

## Limitations

The default Flyway dataset contains 280 historical dashboard orders and the
benchmark adds four live trading customers. This is a reproducible isolation
check, not a production-capacity test. Increase data volume only as a separate,
reviewed benchmark change. Results on a 2-vCPU host are sensitive to Docker
builds, background processes, and the still-running development stack. CPU and
memory metrics are container-level where Docker exposes them; host-level
resource pressure and external EC2 contention are not controlled. PostgreSQL
replication is asynchronous, so reporting results may lag recent orders; the
runner records that lag and waits for the standby to catch up after warmup.

Validation status for this change: the benchmark tooling is implemented, but
no benchmark run is claimed or recorded. The current host fails the memory
guard, so only static validation is available until an isolated host with
sufficient headroom is used. Do not describe the 15% criterion as verified
without committed run output from an approved benchmark host.

# Load test results

Fill this in from a real run. Do not put numbers on your resume that are not in this file.

## How to run

```bash
GATEWAY_RATE=100000 GATEWAY_BURST=100000 TRACING_ENABLED=true \
  docker compose --profile obs up --build --scale booking-service=2
k6 run loadtest/booking.js                 # 10% of bookings are forced to fail (FAIL_RATIO)
```
Watch Grafana at http://localhost:3001 (dashboard "Trip Saga") and traces at http://localhost:16686 (Jaeger) while it runs.

## Environment

| Item | Value |
|---|---|
| Machine (CPU, RAM) | |
| booking-service replicas | 2 |
| Date and git commit | |

## Results

| Metric | Value | Where to read it |
|---|---|---|
| Peak booking rate sustained (per second) | | k6 summary, `http_reqs` / stage |
| Create-booking latency p95 | | k6 `http_req_duration{name:create}` |
| End-to-end saga time p95 | | k6 `saga_e2e_ms` |
| Sagas confirmed / cancelled | | k6 `sagas_confirmed`, `sagas_cancelled` |
| Unfinished sagas | | k6 `sagas_unfinished` (must be 0) |
| Wrong outcomes (a forced failure that confirmed, or the reverse) | | k6 `sagas_wrong_outcome` (must be 0) |

## Failure tests

| Test | Expected | Observed |
|---|---|---|
| Stop Kafka, create a booking, start Kafka | API accepts it; saga completes after Kafka returns | |
| Stop hotel-service mid-load, start it within the timeout | Sagas finish, none lost or duplicated | |
| `CRASH_HOTEL` booking | Retries, lands in `hotel.commands.DLT`, saga times out and compensates | |
| Stop booking-service on the gateway | Circuit opens, 503 fallback, recovers when it returns | |

## Resume bullets (fill the brackets from the tables above)

- Built an event-driven trip booking platform (Spring Boot, Kafka, Redis, React) using orchestrated sagas with compensation and the transactional outbox pattern; sustained [X] bookings/s with p95 end-to-end saga latency of [Y] ms and zero lost or duplicate bookings in load tests.
- Added service discovery, client-side load balancing, circuit breaking and rate limiting at an API gateway; verified recovery from service crashes, broker outages and poison messages.
- Built a LangGraph trip planner with human-in-the-loop approval on a free LLM, with validated output, Redis caching and a deterministic fallback; [Z]% of plan requests served from cache.
- Instrumented services with Prometheus, Grafana and OpenTelemetry traces carried across Kafka through the outbox.

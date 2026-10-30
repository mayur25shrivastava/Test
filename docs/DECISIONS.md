# Design decisions

Short answers to the questions an interviewer will ask. Each states the choice, the alternative, and the trade-off.

| Decision | Alternative | Why this, and what it costs |
|---|---|---|
| **Orchestrated saga** (booking-service drives the steps) | Choreography (services react to each other's events) | The flow and its compensation live in one place, so it is easy to reason about, time out, and show in a UI. Cost: the orchestrator is a central component that must be kept stateless and scaled. |
| **Transactional outbox** | Publish to Kafka directly after the DB commit | Removes the dual-write problem: a crash between the DB write and the publish can no longer lose a message. Cost: extra table and a relay, and delivery becomes at-least-once. |
| **Polling relay by default, Debezium CDC as an option** | CDC only | Polling needs no extra infrastructure and is easy to test. CDC has lower latency and no polling load but needs Kafka Connect, logical replication and a connector per database. |
| **Idempotent consumers (inbox table)** | Rely on exactly-once Kafka | At-least-once delivery plus an idempotent consumer is simpler and works across DB and Kafka. The "seen" marker commits with the business change. |
| **Per-booking partition key** | Random partitioning | All messages of one booking stay ordered. Throughput scales by adding partitions and consumers. |
| **Retry with backoff, then dead-letter topic** | Retry forever | A poison message must not block a partition. The DLT keeps it for inspection and replay. |
| **Saga timeout sweeper + late-reply cleanup** | Assume replies always arrive | A provider can be down or slow. Timeouts compensate; a reservation that arrives after the timeout is detected and released, so nothing is left orphaned. |
| **SSE with a consumer group per instance** | WebSocket, or polling the DB | SSE is one-way and simple. A unique consumer group per instance makes every replica see every update, so a client connected to any replica gets live updates. Cost: stale consumer groups on the broker. |
| **Gateway: client-side load balancing via Eureka, circuit breaker, Redis rate limit** | Server-side LB (nginx/ALB) | Shows service discovery and resilience patterns directly. The SSE route skips the circuit breaker because its time limit would cut long-lived streams. |
| **AI planner is a separate Python service with human approval** | Calling an LLM from the booking path | The LLM is slow and can fail, so it is isolated behind its own breaker and timeout. Nothing is booked without an explicit approval, and model output never reaches booking fields unvalidated. |
| **Validated LLM output with deterministic fallback** | Trust the model | Invalid JSON, unknown ids, rate limits and timeouts all degrade to a built-in planner instead of an error. |

## Known limitations (say these first, it builds trust)

- The rate limiter keys on the client IP; behind the nginx container every user shares one bucket.
- The AI service keeps plans in memory (LangGraph `MemorySaver`); a restart loses pending plans. Use the Postgres checkpointer for persistence.
- The DLT replay endpoint has no authentication.
- Provider services are mocks; there is no real payment or inventory logic.
- Outbox rows are never deleted; add a cleanup job (or delete-after-capture with Debezium).
- Compensation has no timeout of its own: if a provider never answers a CANCEL, the booking stays `COMPENSATING` and needs a retry job or an alert.
- The dead-letter publisher uses the JSON template, so a message that fails *deserialization* is not dead-lettered cleanly; wrap the consumer in `ErrorHandlingDeserializer` for that.
- Hibernate `ddl-auto=update` is used instead of migrations (Flyway would be the next step).

# Milestone 3 code walkthrough: file:line order

Follows one booking across the distribution boundary (commit → event → retry → mock service), then the
failure path and recovery, then observability, delivery and the tests.
Open each item with `~/show3.sh` (type its number; `q` in the file viewer goes back to the menu).
Line numbers match commit `38b7e29`.

| # | What to show | Where |
|---|---|---|
| 1 | Distribution diagram: Client → Appointment Scheduler → Notification Service | `docs/distribution-diagram.png` |
| 2 | Booking transaction (50) ends with `publishEvent` (78), after the insert | `service/SlotBooker.java:50` |
| 3 | Cancel publishes the same kind of event, inside its transaction (86) | `service/AppointmentService.java:66` |
| 4 | Listener: `@Async` (38) + `AFTER_COMMIT` (39), builds the DTO, catch-all (58); message text (65) | `notification/AppointmentNotificationListener.java:38` |
| 5 | `@EnableAsync` | `config/AppConfig.java:14` |
| 6 | The DTO: one coarse-grained request, `keyFor` idempotency key | `notification/NotificationRequest.java:3` |
| 7 | The Gateway interface | `notification/NotificationGateway.java:3` |
| 8 | HTTP gateway: timeouts (28), `Idempotency-Key` header (39), retryable vs not: unreachable (46), 4xx (49), timeout (55) | `notification/RestNotificationGateway.java:26` |
| 9 | Settings: timeouts, attempts, backoff, redelivery interval, base URL | `src/main/resources/application.yml:30` |
| 10 | Retry loop (35), stop on 4xx / last attempt (46), backoff 200 → 400 ms (48), fallback (56) | `notification/NotificationSender.java:32` |
| 11 | Fallback: FAILED for 4xx (63), PENDING for redelivery (70) | `notification/NotificationSender.java:59` |
| 12 | Fallback table `pending_notifications` | `src/main/resources/schema.sql:78` |
| 13 | `park`: `ON CONFLICT (idempotency_key) DO NOTHING` (35) | `notification/PendingNotificationRepository.java:30` |
| 14 | Recovery job: every 30 s (33), give-up rule (48) | `notification/NotificationRedeliveryJob.java:33` |
| 15 | Mock service: DOWN → 503 (56), SLOW (61), store once per key (68); mode switch (88) | `mocknotify/MockNotificationController.java:42` |
| 16 | Dedup by key: `computeIfAbsent` | `mocknotify/MockNotificationStore.java:38` |
| 17 | Mock's own request class (no shared Java classes) | `mocknotify/MockNotificationMessage.java:6` |
| 18 | Security: `/health`, metrics, `/mock-notify/**` rules | `security/SecurityConfig.java:62` |
| 19 | `GET /health`: `SELECT 1` (45), DEGRADED (52), 200 / 503 (68) | `controller/HealthController.java:18` |
| 20 | Metric: timer start (50), stop with the `outcome` tag (67) | `service/BookingService.java:45` |
| 21 | Dockerfile with `HEALTHCHECK` | `Dockerfile:15` |
| 22 | docker-compose: app + PostgreSQL, `NOTIFICATION_BASE_URL` | `docker-compose.yml:18` |
| 23 | GitHub Actions: on every push, `mvn -B verify`, reports, Docker build | `.github/workflows/ci.yml:3` |
| 24 | Required test: service unreachable (25), booking + cancel succeed (50) | `test/NotificationDownIntegrationTest.java:25` |
| 25 | Slow service → timeout → parked → redelivered, stored once | `test/NotificationIntegrationTest.java:87` |
| 26 | One notification per book and per cancel, over real HTTP | `test/NotificationIntegrationTest.java:68` |
| 27 | Retry / fallback unit tests | `test/NotificationSenderTest.java:64` |
| 28 | Event published once + metric counted | `test/BookingServiceTest.java:86` |
| 29 | Test results (79 tests) and notification log lines | `docs/m3-test-output.txt:1` |

Paths in the table are under `src/main/java/edu/sjsu/cmpe172/scheduler/` (`test/` = `src/test/java/...`).

## Live demos in `~/show3.sh` (app must be running: `docker compose up --build -d`)

| Key | What it does |
|---|---|
| `d` | Notification service **DOWN**: creates a slot, books it (prints the time taken), shows the retries + "parked" log lines and `/health` DEGRADED; on Enter switches the mock UP, waits for the redelivery job, shows `/health` UP and the delivered message |
| `s` | Same with the mock **SLOW** (5 s answer vs 2 s read timeout) |
| `x` | Database down: stops the db container, `/health` → 503, starts it again, `/health` → 200 |
| `h` | `GET /health` |
| `m` | Metrics: `scheduler.booking`, `scheduler.notifications` |
| `n` | What the mock service has received |
| `l` | Last 25 notification log lines (INFO / WARN / ERROR) |
| `t` | Runs `NotificationDownIntegrationTest` live (needs Docker Desktop) |
| `c` | Opens the GitHub Actions page (CI run for the latest push) |

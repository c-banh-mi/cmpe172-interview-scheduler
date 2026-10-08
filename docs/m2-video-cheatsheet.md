# Milestone 2 code walkthrough: file:line order

Follows one booking request from the browser to the database and back, then the safety nets and the proof.
Open each item with `~/show2.sh` (type its number).

| # | What to show | Where |
|---|---|---|
| 1 | Block diagram with the new Spring Security layer | `docs/block-diagram.png` |
| 2 | Booking sequence: filters → controller → retry loop → transaction → SQL, with the 0-rows/retry and 409 paths | `docs/booking-sequence.png` |
| 3 | Frontend: `api()` sends JSON, the session cookie and the `X-XSRF-TOKEN` header | `src/main/resources/static/app.js:23` |
| 4 | Frontend: `bookPage()` posts the booking (line 250), shows the confirmation (254) or the 409 banner | `src/main/resources/static/app.js:216` |
| 5 | SecurityConfig: URL role rules (56-66), form login (67-76), JSON 401/403 handlers (80-84), CSRF (87-90) | `src/main/java/edu/sjsu/cmpe172/scheduler/security/SecurityConfig.java:53` |
| 6 | Login lookup: role read from the users row by a JDBC query | `src/main/java/edu/sjsu/cmpe172/scheduler/repository/UserRepository.java:28` |
| 7 | Role becomes the authority `ROLE_CUSTOMER` / `ROLE_PROVIDER` | `src/main/java/edu/sjsu/cmpe172/scheduler/security/UserPrincipal.java:17` |
| 8 | Controller: `@Valid BookingRequest`, current user from the session, 201 Created | `src/main/java/edu/sjsu/cmpe172/scheduler/controller/CustomerController.java:35` |
| 9 | Validation rules on the request body | `src/main/java/edu/sjsu/cmpe172/scheduler/dto/BookingRequest.java:8` |
| 10 | BookingService: role check (42), retry loop (46-66), back-off (70-77) | `src/main/java/edu/sjsu/cmpe172/scheduler/service/BookingService.java:39` |
| 11 | SlotBooker: `@Transactional(READ_COMMITTED)` (44), booking rules (46-60), version check (63-65), insert (68) | `src/main/java/edu/sjsu/cmpe172/scheduler/service/SlotBooker.java:36` |
| 12 | The version-check UPDATE and why the loser matches 0 rows | `src/main/java/edu/sjsu/cmpe172/scheduler/repository/SlotRepository.java:102` |
| 13 | Schema: slot uniqueness change (48-53), `version` column (42), backstop `uq_appt_active_slot` (72-74) | `src/main/resources/schema.sql:42` |
| 14 | Owner-only cancel (60-62), conditional update (70), slot reopened in the same transaction (73) | `src/main/java/edu/sjsu/cmpe172/scheduler/service/AppointmentService.java:51` |
| 15 | GlobalExceptionHandler: 400 / 404 / 403 / 409, generic 500 without a stack trace | `src/main/java/edu/sjsu/cmpe172/scheduler/exception/GlobalExceptionHandler.java:22` |
| 16 | Two-thread test: barrier (54), threads (55-73), asserts (75-85) | `src/test/java/edu/sjsu/cmpe172/scheduler/ConcurrentBookingTest.java:44` |
| 17 | Unit tests for the booking rules and retry | `src/test/java/edu/sjsu/cmpe172/scheduler/BookingServiceTest.java:65` |
| 18 | Test results (67 tests, race log) | `docs/test-output.txt:1` |

Snippets and the schema-change list for the report: [`m2-report-material.md`](m2-report-material.md).

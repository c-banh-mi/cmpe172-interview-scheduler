# Interview Scheduler

CMPE 172 term project: an **Online Appointment Scheduling System** where software engineers book
**resume reviews and mock interviews** (coding, system design, behavioral) with experienced mentors.

- **Customer**: a job-seeking engineer who browses and books sessions
- **Provider**: a mentor/interviewer who publishes availability

Stack: **Java 21, Spring Boot 3.5, PostgreSQL 16, plain JDBC (`JdbcClient`) with hand-written SQL. No ORM.**

> Note: `hibernate-validator` appears in the dependency tree. It is the Bean Validation (`@NotNull`, `@Size`)
> implementation pulled in by `spring-boot-starter-validation`, **not** Hibernate ORM. There is no JPA/Hibernate ORM on the classpath.

## Milestone 1: what's implemented

- Layered skeleton: Controller → Service → Repository (JDBC) → PostgreSQL
- `schema.sql` (all 5 tables + double-booking guard) and `seed.sql`, loaded on every startup
- Read-only endpoints that return DTOs (never raw rows):

| Method | Path | Description |
|---|---|---|
| GET | `/` | Minimal HTML page that calls the API below |
| GET | `/api/home` | App summary: services, providers, open-slot count |
| GET | `/api/slots` | Open future slots. Query params: `providerId`, `serviceId`, `date` (YYYY-MM-DD), `page` (0-based), `size` (1–50). Paginated with SQL `LIMIT/OFFSET` |
| GET | `/api/services` | All services |
| GET | `/api/providers` | All providers |
| GET | `/actuator/health` | Health check |

- Global exception handler (JSON errors with proper status codes)
- Tests: unit (`SlotServiceTest`) + integration against real PostgreSQL via Testcontainers (`ApiIntegrationTest`)
- Dockerfile and docker-compose.yml
- GitHub Actions CI builds and runs all tests on every push ([Actions](https://github.com/c-banh-mi/cmpe172-interview-scheduler/actions))

Design docs:
- ER diagram: [`docs/er-diagram.png`](docs/er-diagram.png) (Mermaid source: [`docs/er-diagram.mmd`](docs/er-diagram.mmd))
- Block diagram: [`docs/block-diagram.png`](docs/block-diagram.png) (Mermaid source: [`docs/block-diagram.mmd`](docs/block-diagram.mmd))
- Relational schema: [`docs/relational-schema.md`](docs/relational-schema.md)
- Wireframes (Home, Available slots, Book appointment, Confirmation): [`docs/wireframes/`](docs/wireframes/) and [`docs/wireframes.pdf`](docs/wireframes.pdf)

### Double-booking guard

```sql
CREATE UNIQUE INDEX uq_appt_active_slot ON appointments (slot_id) WHERE status = 'BOOKED';
```
Plus `UNIQUE (provider_id, start_time)` on `availability_slots` and a `version` column for
optimistic locking (used when booking is built in Milestone 2).

## Prerequisites

- JDK 21, Maven 3.8+
- Docker (for PostgreSQL and for the integration tests)

## Setup

Create your local `.env` (gitignored; never commit it). Docker Compose needs it for both build and run:
```bash
cp .env.example .env    # then edit DB_PASSWORD
```

## Build

```bash
mvn -B package          # compile, run tests (needs Docker), produce target/interview-scheduler-*.jar
docker compose build    # build the app's Docker image (Maven runs inside the image; tests skipped)
```

## Run

**Option A: everything in Docker**
```bash
docker compose up --build
# open http://localhost:8080
```

**Option B: DB in Docker, app from Maven (for development)**
```bash
docker compose up -d db
export $(grep -v '^#' .env | xargs)   # load DB_USERNAME / DB_PASSWORD into the shell
mvn spring-boot:run
```

Note: the schema is dropped and re-seeded on every startup (Milestone 1 dev behavior).

## Configuration

| Env var | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/scheduler` |
| `DB_USERNAME` | `scheduler` |
| `DB_PASSWORD` | _none: set it in `.env`_ |
| `PORT` | `8080` |

Seeded accounts (password `password123`, stored as BCrypt): providers `alice.mentor`, `raj.mentor`,
`maria.mentor`; customers `sam.dev`, `jordan.dev`. Login arrives in Milestone 2.

## Test

```bash
mvn test        # needs Docker running (Testcontainers starts PostgreSQL)
```

## Try it

```bash
curl http://localhost:8080/api/home
curl "http://localhost:8080/api/slots?page=0&size=5"
curl "http://localhost:8080/api/slots?serviceId=1&date=$(date -d tomorrow +%F)"
```

## Code walkthrough video

- Milestone 1: _TODO: add link_

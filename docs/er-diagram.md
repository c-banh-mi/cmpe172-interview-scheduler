# ER Diagram

Rendered automatically by GitHub (Mermaid). Source of truth: `src/main/resources/schema.sql`.

```mermaid
erDiagram
    users ||--o| providers : "has profile (PROVIDER role)"
    users ||--o{ appointments : "books (CUSTOMER role)"
    providers ||--o{ availability_slots : "offers"
    services ||--o{ availability_slots : "is offered in"
    availability_slots ||--o{ appointments : "booked as (max 1 BOOKED)"
    services ||--o{ appointments : "for"

    users {
        BIGSERIAL id PK
        VARCHAR username UK
        VARCHAR password_hash "BCrypt"
        VARCHAR full_name
        VARCHAR email UK
        VARCHAR role "CUSTOMER | PROVIDER"
        TIMESTAMP created_at
    }
    providers {
        BIGSERIAL id PK
        BIGINT user_id FK,UK
        VARCHAR display_name
        VARCHAR headline
        TEXT bio
    }
    services {
        BIGSERIAL id PK
        VARCHAR name UK
        TEXT description
        INT duration_minutes
        INT price_cents
    }
    availability_slots {
        BIGSERIAL id PK
        BIGINT provider_id FK
        BIGINT service_id FK
        TIMESTAMP start_time "UK with provider_id"
        TIMESTAMP end_time
        VARCHAR status "OPEN | BOOKED | REMOVED"
        INT version "optimistic lock"
    }
    appointments {
        BIGSERIAL id PK
        BIGINT slot_id FK "unique WHERE status=BOOKED"
        BIGINT service_id FK "(slot_id, service_id) -> slot"
        BIGINT customer_id FK
        VARCHAR status "BOOKED | CANCELLED | COMPLETED"
        TEXT notes
        TIMESTAMP created_at
        TIMESTAMP cancelled_at
    }
```

## Block diagram

```mermaid
flowchart LR
    C[Browser<br/>static index.html / API client] -- HTTP JSON --> A
    subgraph A[Spring Boot app]
        direction TB
        DS[DispatcherServlet<br/>Front Controller] --> CT[Controllers] --> SV[Services<br/>@Transactional] --> RP[Repositories<br/>JdbcClient + SQL]
    end
    RP -- JDBC --> DB[(PostgreSQL)]
    SV -. "commit, then notify (Milestone 3)" .-> N[Mock Notification Service]
```

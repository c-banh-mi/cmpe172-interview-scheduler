# Relational Schema

Source: `src/main/resources/schema.sql` (PostgreSQL 16). Constraint names without an explicit name in `schema.sql` are the names PostgreSQL generates.

## users

| Column | Type | Nullable | Default |
|---|---|---|---|
| id | BIGSERIAL (bigint) | NO | nextval('users_id_seq') |
| username | VARCHAR(50) | NO | |
| password_hash | VARCHAR(100) | NO | |
| full_name | VARCHAR(100) | NO | |
| email | VARCHAR(255) | NO | |
| role | VARCHAR(20) | NO | |
| created_at | TIMESTAMP | NO | CURRENT_TIMESTAMP |

- **PK:** `users_pkey` (id)
- **FKs:** none
- **UNIQUE:** `users_username_key` (username); `users_email_key` (email)
- **CHECK:** `users_role_check`: role IN ('CUSTOMER', 'PROVIDER')
- **Indexes:** `users_pkey`, `users_username_key`, `users_email_key` (unique B-tree indexes backing the constraints)

## providers

| Column | Type | Nullable | Default |
|---|---|---|---|
| id | BIGSERIAL (bigint) | NO | nextval('providers_id_seq') |
| user_id | BIGINT | NO | |
| display_name | VARCHAR(100) | NO | |
| headline | VARCHAR(200) | NO | |
| bio | TEXT | YES | |

- **PK:** `providers_pkey` (id)
- **FKs:** `providers_user_id_fkey`: user_id → users(id) ON DELETE CASCADE
- **UNIQUE:** `providers_user_id_key` (user_id)
- **CHECK:** none
- **Indexes:** `providers_pkey`, `providers_user_id_key` (unique B-tree indexes backing the constraints)

## services

| Column | Type | Nullable | Default |
|---|---|---|---|
| id | BIGSERIAL (bigint) | NO | nextval('services_id_seq') |
| name | VARCHAR(100) | NO | |
| description | TEXT | YES | |
| duration_minutes | INT | NO | |
| price_cents | INT | NO | |

- **PK:** `services_pkey` (id)
- **FKs:** none
- **UNIQUE:** `services_name_key` (name)
- **CHECK:** `services_duration_minutes_check`: duration_minutes > 0; `services_price_cents_check`: price_cents >= 0
- **Indexes:** `services_pkey`, `services_name_key` (unique B-tree indexes backing the constraints)

## availability_slots

| Column | Type | Nullable | Default |
|---|---|---|---|
| id | BIGSERIAL (bigint) | NO | nextval('availability_slots_id_seq') |
| provider_id | BIGINT | NO | |
| service_id | BIGINT | NO | |
| start_time | TIMESTAMP | NO | |
| end_time | TIMESTAMP | NO | |
| status | VARCHAR(20) | NO | 'OPEN' |
| version | INT | NO | 0 |

- **PK:** `availability_slots_pkey` (id)
- **FKs:**
  - `availability_slots_provider_id_fkey`: provider_id → providers(id) ON DELETE CASCADE
  - `availability_slots_service_id_fkey`: service_id → services(id)
- **UNIQUE:** `uq_slot_provider_start` (provider_id, start_time); `uq_slot_id_service` (id, service_id)
- **CHECK:** `availability_slots_status_check`: status IN ('OPEN', 'BOOKED', 'REMOVED'); `ck_slot_time_order`: end_time > start_time
- **Indexes:**
  - `availability_slots_pkey`, `uq_slot_provider_start`, `uq_slot_id_service` (unique B-tree indexes backing the constraints)
  - `ix_slots_open_start`: B-tree on (status, start_time)

## appointments

| Column | Type | Nullable | Default |
|---|---|---|---|
| id | BIGSERIAL (bigint) | NO | nextval('appointments_id_seq') |
| slot_id | BIGINT | NO | |
| service_id | BIGINT | NO | |
| customer_id | BIGINT | NO | |
| status | VARCHAR(20) | NO | 'BOOKED' |
| notes | TEXT | YES | |
| created_at | TIMESTAMP | NO | CURRENT_TIMESTAMP |
| cancelled_at | TIMESTAMP | YES | |

- **PK:** `appointments_pkey` (id)
- **FKs:**
  - `fk_appt_slot_service`: (slot_id, service_id) → availability_slots(id, service_id)
  - `appointments_customer_id_fkey`: customer_id → users(id)
- **UNIQUE:** none as table constraints (see partial unique index below)
- **CHECK:** `appointments_status_check`: status IN ('BOOKED', 'CANCELLED', 'COMPLETED')
- **Indexes:**
  - `appointments_pkey` (unique B-tree index backing the PK)
  - `uq_appt_active_slot`: **UNIQUE** B-tree on (slot_id) **WHERE status = 'BOOKED'** (double-booking guard)
  - `ix_appt_customer`: B-tree on (customer_id)

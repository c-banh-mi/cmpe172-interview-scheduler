-- Interview Scheduler schema (PostgreSQL). All SQL hand-written; no ORM.
-- Runs on every startup but only creates what is missing, so bookings survive restarts.
-- To start over: docker compose down -v (deletes the database volume).

-- Every account. Role decides which endpoints the account may use.
CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,            -- BCrypt hash, never plaintext
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER', 'PROVIDER')),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Provider profile (a mentor / interviewer). 1:1 with a PROVIDER user.
CREATE TABLE IF NOT EXISTS providers (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    display_name VARCHAR(100) NOT NULL,
    headline     VARCHAR(200) NOT NULL,              -- e.g. "Senior SWE, 8 yrs backend"
    bio          TEXT
);

-- Bookable service types (resume review, mock interviews, ...).
CREATE TABLE IF NOT EXISTS services (
    id               BIGSERIAL    PRIMARY KEY,
    name             VARCHAR(100) NOT NULL UNIQUE,
    description      TEXT,
    duration_minutes INT          NOT NULL CHECK (duration_minutes > 0),
    price_cents      INT          NOT NULL CHECK (price_cents >= 0)
);

-- An open time window a provider offers for one service.
CREATE TABLE IF NOT EXISTS availability_slots (
    id          BIGSERIAL   PRIMARY KEY,
    provider_id BIGINT      NOT NULL REFERENCES providers (id) ON DELETE CASCADE,
    service_id  BIGINT      NOT NULL REFERENCES services (id),
    start_time  TIMESTAMP   NOT NULL,
    end_time    TIMESTAMP   NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'BOOKED', 'REMOVED')),
    version     INT         NOT NULL DEFAULT 0,      -- optimistic-lock counter, +1 on every status change
    CONSTRAINT ck_slot_time_order CHECK (end_time > start_time),
    -- Target for the composite FK below (lets appointments prove slot/service agree).
    CONSTRAINT uq_slot_id_service UNIQUE (id, service_id)
);

-- Upgrade from Milestone 1, where this was a plain UNIQUE constraint (no-op on a new database).
ALTER TABLE availability_slots DROP CONSTRAINT IF EXISTS uq_slot_provider_start;
-- A provider cannot publish two live slots that start at the same moment.
-- REMOVED slots are excluded so a provider can re-add a time they removed.
CREATE UNIQUE INDEX IF NOT EXISTS uq_slot_provider_start ON availability_slots (provider_id, start_time)
    WHERE status <> 'REMOVED';

CREATE INDEX IF NOT EXISTS ix_slots_open_start ON availability_slots (status, start_time);

-- A booking: links a customer, a slot and the slot's service.
CREATE TABLE IF NOT EXISTS appointments (
    id           BIGSERIAL   PRIMARY KEY,
    slot_id      BIGINT      NOT NULL,
    service_id   BIGINT      NOT NULL,
    customer_id  BIGINT      NOT NULL REFERENCES users (id),
    status       VARCHAR(20) NOT NULL DEFAULT 'BOOKED' CHECK (status IN ('BOOKED', 'CANCELLED', 'COMPLETED')),
    notes        TEXT,                                -- e.g. target role, resume link
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMP,
    -- The appointment's service must be the slot's service.
    CONSTRAINT fk_appt_slot_service FOREIGN KEY (slot_id, service_id)
        REFERENCES availability_slots (id, service_id)
);

-- DOUBLE-BOOKING GUARD: at most one *active* (BOOKED) appointment per slot.
-- Cancelled rows stay for history and don't block rebooking the slot.
CREATE UNIQUE INDEX IF NOT EXISTS uq_appt_active_slot ON appointments (slot_id) WHERE status = 'BOOKED';

CREATE INDEX IF NOT EXISTS ix_appt_customer ON appointments (customer_id);

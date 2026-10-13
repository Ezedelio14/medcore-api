-- MedCore Healthcare Platform - initial schema
-- Enum-like columns use VARCHAR + CHECK constraints so they map cleanly to @Enumerated(STRING)
-- and can evolve without ALTER TYPE migrations.

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id                 UUID PRIMARY KEY,
    email              VARCHAR(255) NOT NULL,
    password_hash      VARCHAR(255) NOT NULL,
    role               VARCHAR(32)  NOT NULL CHECK (role IN ('PATIENT', 'DOCTOR', 'RECEPTIONIST', 'CLINIC_ADMIN', 'SUPER_ADMIN')),
    status             VARCHAR(32)  NOT NULL CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DELETED')),
    email_verified     BOOLEAN      NOT NULL DEFAULT FALSE,
    two_factor_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX ux_users_email ON users (LOWER(email));

-- ---------------------------------------------------------------------------
-- clinics
-- ---------------------------------------------------------------------------
CREATE TABLE clinics (
    id                           UUID PRIMARY KEY,
    name                         VARCHAR(200) NOT NULL,
    legal_name                   VARCHAR(255),
    email                        VARCHAR(255),
    phone                        VARCHAR(40),
    address                      VARCHAR(255),
    city                         VARCHAR(120),
    country                      VARCHAR(2),
    timezone                     VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    default_appointment_duration INTEGER      NOT NULL DEFAULT 30 CHECK (default_appointment_duration > 0),
    status                       VARCHAR(32)  NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    created_at                   TIMESTAMPTZ  NOT NULL,
    updated_at                   TIMESTAMPTZ  NOT NULL
);

CREATE TABLE clinic_opening_hours (
    clinic_id   UUID        NOT NULL REFERENCES clinics (id) ON DELETE CASCADE,
    day_of_week VARCHAR(16) NOT NULL CHECK (day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    open_time   TIME        NOT NULL,
    close_time  TIME        NOT NULL,
    CHECK (close_time > open_time),
    PRIMARY KEY (clinic_id, day_of_week)
);

-- Receptionists and clinic admins attached to a clinic
CREATE TABLE clinic_staff (
    id         UUID PRIMARY KEY,
    clinic_id  UUID        NOT NULL REFERENCES clinics (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (clinic_id, user_id)
);

-- ---------------------------------------------------------------------------
-- patients
-- ---------------------------------------------------------------------------
CREATE TABLE patients (
    id                 UUID PRIMARY KEY,
    user_id            UUID         NOT NULL UNIQUE REFERENCES users (id),
    clinic_id          UUID REFERENCES clinics (id),
    first_name         VARCHAR(100) NOT NULL,
    last_name          VARCHAR(100) NOT NULL,
    date_of_birth      DATE,
    gender             VARCHAR(16) CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'UNDISCLOSED')),
    phone              VARCHAR(40),
    emergency_contact  VARCHAR(255),
    blood_type         VARCHAR(4) CHECK (blood_type IN ('A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-')),
    allergies          TEXT,
    stripe_customer_id VARCHAR(64),
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_patients_clinic ON patients (clinic_id);

-- ---------------------------------------------------------------------------
-- doctors & specialties
-- ---------------------------------------------------------------------------
CREATE TABLE doctors (
    id                 UUID PRIMARY KEY,
    user_id            UUID           NOT NULL UNIQUE REFERENCES users (id),
    clinic_id          UUID           NOT NULL REFERENCES clinics (id),
    first_name         VARCHAR(100)   NOT NULL,
    last_name          VARCHAR(100)   NOT NULL,
    license_number     VARCHAR(64)    NOT NULL UNIQUE,
    biography          TEXT,
    consultation_price NUMERIC(12, 2) NOT NULL CHECK (consultation_price >= 0),
    status             VARCHAR(32)    NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'ON_LEAVE')),
    created_at         TIMESTAMPTZ    NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL
);
CREATE INDEX ix_doctors_clinic ON doctors (clinic_id);

CREATE TABLE specialties (
    id          UUID PRIMARY KEY,
    name        VARCHAR(120) NOT NULL UNIQUE,
    description TEXT
);

CREATE TABLE doctor_specialties (
    doctor_id    UUID NOT NULL REFERENCES doctors (id) ON DELETE CASCADE,
    specialty_id UUID NOT NULL REFERENCES specialties (id) ON DELETE CASCADE,
    PRIMARY KEY (doctor_id, specialty_id)
);

-- ---------------------------------------------------------------------------
-- schedules (weekly availability templates per doctor)
-- ---------------------------------------------------------------------------
CREATE TABLE schedules (
    id                   UUID PRIMARY KEY,
    doctor_id            UUID        NOT NULL REFERENCES doctors (id) ON DELETE CASCADE,
    day_of_week          VARCHAR(16) NOT NULL CHECK (day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    start_time           TIME        NOT NULL,
    end_time             TIME        NOT NULL,
    appointment_duration INTEGER     NOT NULL CHECK (appointment_duration > 0),
    active               BOOLEAN     NOT NULL DEFAULT TRUE,
    CHECK (end_time > start_time)
);
CREATE INDEX ix_schedules_doctor_day ON schedules (doctor_id, day_of_week) WHERE active;

-- ---------------------------------------------------------------------------
-- appointments
-- ---------------------------------------------------------------------------
CREATE TABLE appointments (
    id                  UUID PRIMARY KEY,
    patient_id          UUID           NOT NULL REFERENCES patients (id),
    doctor_id           UUID           NOT NULL REFERENCES doctors (id),
    clinic_id           UUID           NOT NULL REFERENCES clinics (id),
    scheduled_at        TIMESTAMPTZ    NOT NULL,
    ends_at             TIMESTAMPTZ    NOT NULL,
    duration            INTEGER        NOT NULL CHECK (duration > 0),
    status              VARCHAR(32)    NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    reason              TEXT,
    cancellation_reason TEXT,
    price               NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    payment_status      VARCHAR(32)    NOT NULL CHECK (payment_status IN ('UNPAID', 'PENDING', 'PAID', 'REFUNDED', 'FAILED')),
    reminder_sent_at    TIMESTAMPTZ,
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL,
    CHECK (ends_at > scheduled_at),
    -- Database-level guarantee against double booking: no two active appointments
    -- of the same doctor may overlap in time.
    CONSTRAINT ex_appointments_no_double_booking EXCLUDE USING gist (
        doctor_id WITH =,
        tstzrange(scheduled_at, ends_at, '[)') WITH &&
    ) WHERE (status IN ('PENDING', 'CONFIRMED'))
);
CREATE INDEX ix_appointments_doctor_time ON appointments (doctor_id, scheduled_at);
CREATE INDEX ix_appointments_patient_time ON appointments (patient_id, scheduled_at);
CREATE INDEX ix_appointments_clinic_time ON appointments (clinic_id, scheduled_at);
CREATE INDEX ix_appointments_reminders ON appointments (scheduled_at) WHERE status = 'CONFIRMED' AND reminder_sent_at IS NULL;

-- ---------------------------------------------------------------------------
-- medical records & prescriptions
-- ---------------------------------------------------------------------------
CREATE TABLE medical_records (
    id             UUID PRIMARY KEY,
    patient_id     UUID        NOT NULL REFERENCES patients (id),
    doctor_id      UUID        NOT NULL REFERENCES doctors (id),
    appointment_id UUID REFERENCES appointments (id),
    diagnosis      TEXT,
    observations   TEXT,
    treatment      TEXT,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_medical_records_patient ON medical_records (patient_id, created_at DESC);

CREATE TABLE prescriptions (
    id                UUID PRIMARY KEY,
    patient_id        UUID        NOT NULL REFERENCES patients (id),
    doctor_id         UUID        NOT NULL REFERENCES doctors (id),
    medical_record_id UUID REFERENCES medical_records (id),
    issued_at         TIMESTAMPTZ,
    notes             TEXT,
    status            VARCHAR(32) NOT NULL CHECK (status IN ('DRAFT', 'ISSUED', 'CANCELLED', 'EXPIRED'))
);
CREATE INDEX ix_prescriptions_patient ON prescriptions (patient_id);

CREATE TABLE prescription_items (
    id              UUID PRIMARY KEY,
    prescription_id UUID         NOT NULL REFERENCES prescriptions (id) ON DELETE CASCADE,
    medication_name VARCHAR(255) NOT NULL,
    dosage          VARCHAR(120) NOT NULL,
    frequency       VARCHAR(120) NOT NULL,
    duration        VARCHAR(120) NOT NULL,
    instructions    TEXT
);

-- ---------------------------------------------------------------------------
-- payments
-- ---------------------------------------------------------------------------
CREATE TABLE payments (
    id                  UUID PRIMARY KEY,
    appointment_id      UUID           NOT NULL REFERENCES appointments (id),
    patient_id          UUID           NOT NULL REFERENCES patients (id),
    amount              NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    currency            VARCHAR(3)     NOT NULL,
    provider            VARCHAR(32)    NOT NULL CHECK (provider IN ('STRIPE', 'CLINIC', 'INSURANCE')),
    provider_payment_id VARCHAR(255),
    status              VARCHAR(32)    NOT NULL CHECK (status IN ('PENDING', 'REQUIRES_ACTION', 'SUCCEEDED', 'FAILED', 'REFUNDED', 'CANCELLED')),
    failure_reason      TEXT,
    paid_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL
);
CREATE INDEX ix_payments_appointment ON payments (appointment_id);
CREATE UNIQUE INDEX ux_payments_provider_payment_id ON payments (provider_payment_id) WHERE provider_payment_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- documents, notifications, reviews, audit
-- ---------------------------------------------------------------------------
CREATE TABLE documents (
    id             UUID PRIMARY KEY,
    patient_id     UUID         NOT NULL REFERENCES patients (id),
    appointment_id UUID REFERENCES appointments (id),
    type           VARCHAR(32)  NOT NULL CHECK (type IN ('PRESCRIPTION', 'MEDICAL_REPORT', 'LAB_RESULT', 'IMAGING', 'RECEIPT', 'OTHER')),
    file_name      VARCHAR(255) NOT NULL,
    storage_key    VARCHAR(512) NOT NULL UNIQUE,
    mime_type      VARCHAR(127) NOT NULL,
    size           BIGINT       NOT NULL CHECK (size >= 0),
    created_at     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_documents_patient ON documents (patient_id);

CREATE TABLE notifications (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(48)  NOT NULL,
    title      VARCHAR(255) NOT NULL,
    message    TEXT         NOT NULL,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_notifications_user_unread ON notifications (user_id, created_at DESC) WHERE read_at IS NULL;

CREATE TABLE reviews (
    id             UUID PRIMARY KEY,
    patient_id     UUID        NOT NULL REFERENCES patients (id),
    doctor_id      UUID        NOT NULL REFERENCES doctors (id),
    appointment_id UUID        NOT NULL UNIQUE REFERENCES appointments (id),
    rating         SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment        TEXT,
    created_at     TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_reviews_doctor ON reviews (doctor_id);

CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY,
    user_id     UUID,
    action      VARCHAR(64)  NOT NULL,
    entity_type VARCHAR(64),
    entity_id   UUID,
    metadata    JSONB,
    created_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX ix_audit_logs_user ON audit_logs (user_id, created_at DESC);

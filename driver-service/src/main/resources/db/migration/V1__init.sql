-- Schema cua driver-service: ho so, phuong tien, tai lieu, quyet dinh duyet. Database rieng, chi luu ID cua service khac.

-- Chan UPDATE/DELETE tren bang bat bien: lich su trang thai, ledger, audit (DR-005, FR-ADM-006).
CREATE FUNCTION prevent_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Table % is append-only', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

-- =====================================================================
-- platform
-- =====================================================================
CREATE TABLE outbox_events (
    id               uuid PRIMARY KEY,
    aggregate_type   varchar(50)  NOT NULL,
    aggregate_id     varchar(64)  NOT NULL,
    event_type       varchar(80)  NOT NULL,
    event_version    int          NOT NULL,
    payload          text         NOT NULL,
    correlation_id   varchar(64),
    producer         varchar(50)  NOT NULL,
    occurred_at      timestamptz  NOT NULL,
    status           varchar(20)  NOT NULL,
    attempts         int          NOT NULL DEFAULT 0,
    next_attempt_at  timestamptz  NOT NULL,
    last_error       varchar(1000),
    processed_at     timestamptz
);
CREATE INDEX idx_outbox_pending ON outbox_events (next_attempt_at) WHERE status = 'NEW';
CREATE INDEX idx_outbox_status ON outbox_events (status, occurred_at);

CREATE TABLE processed_events (
    event_id      uuid        NOT NULL,
    handler       varchar(100) NOT NULL,
    processed_at  timestamptz NOT NULL,
    PRIMARY KEY (event_id, handler)
);

CREATE TABLE audit_records (
    id              uuid PRIMARY KEY,
    actor_id        uuid,
    actor_roles     varchar(200),
    action          varchar(80)  NOT NULL,
    target_type     varchar(50)  NOT NULL,
    target_id       varchar(64)  NOT NULL,
    result          varchar(20)  NOT NULL,
    delta           text,
    correlation_id  varchar(64),
    created_at      timestamptz  NOT NULL
);
CREATE INDEX idx_audit_target ON audit_records (target_type, target_id);
CREATE INDEX idx_audit_created ON audit_records (created_at DESC);
CREATE TRIGGER trg_audit_immutable BEFORE UPDATE OR DELETE ON audit_records
    FOR EACH ROW EXECUTE FUNCTION prevent_mutation();

CREATE TABLE idempotency_records (
    id             uuid PRIMARY KEY,
    user_id        uuid         NOT NULL,
    scope          varchar(60)  NOT NULL,
    idem_key       varchar(100) NOT NULL,
    request_hash   varchar(64)  NOT NULL,
    status         varchar(20)  NOT NULL,
    response_body  text,
    created_at     timestamptz  NOT NULL,
    completed_at   timestamptz,
    CONSTRAINT uq_idempotency UNIQUE (user_id, scope, idem_key)
);

-- Event nhan tu service khac qua Kafka: luu truoc, xu ly sau (inbox), de consumer
-- thu lai duoc va idempotent ngay ca khi service chet giua chung (FR-EVT-004, FR-EVT-005).
CREATE TABLE inbox_events (
    id               uuid PRIMARY KEY,           -- = eventId cua service phat
    aggregate_type   varchar(50)  NOT NULL,
    aggregate_id     varchar(64)  NOT NULL,
    event_type       varchar(80)  NOT NULL,
    event_version    int          NOT NULL,
    payload          text         NOT NULL,
    correlation_id   varchar(64),
    producer         varchar(50)  NOT NULL,
    occurred_at      timestamptz  NOT NULL,
    received_at      timestamptz  NOT NULL,
    status           varchar(20)  NOT NULL,
    attempts         int          NOT NULL DEFAULT 0,
    next_attempt_at  timestamptz  NOT NULL,
    last_error       varchar(1000),
    processed_at     timestamptz
);
CREATE INDEX idx_inbox_pending ON inbox_events (next_attempt_at) WHERE status = 'NEW';
CREATE INDEX idx_inbox_status ON inbox_events (status, occurred_at);

-- =====================================================================
-- driver
-- =====================================================================
CREATE TABLE driver_profiles (
    id                       uuid PRIMARY KEY,           -- = users.id cua tai khoan tai xe
    license_number           varchar(30),
    license_class            varchar(10),
    license_expiry           date,
    national_id_number       varchar(20),
    address                  varchar(255),
    review_status            varchar(20)  NOT NULL,
    review_reason            varchar(500),
    profile_version          int          NOT NULL DEFAULT 1,
    -- Ban chup tu identity (UserRegistered / UserProfileUpdated / UserStatusChanged), khong goi nguoc identity
    full_name                varchar(100),
    phone                    varchar(20),
    account_active           boolean      NOT NULL DEFAULT true,
    submitted_at             timestamptz,
    approved_at              timestamptz,
    created_at               timestamptz  NOT NULL,
    updated_at               timestamptz  NOT NULL,
    version                  bigint       NOT NULL DEFAULT 0
);
CREATE INDEX idx_driver_review ON driver_profiles (review_status);

CREATE TABLE vehicles (
    id                uuid PRIMARY KEY,
    driver_id         uuid        NOT NULL REFERENCES driver_profiles (id),
    vehicle_type      varchar(20) NOT NULL,
    plate_number      varchar(15) NOT NULL,
    brand             varchar(50) NOT NULL,
    model             varchar(50) NOT NULL,
    color             varchar(30) NOT NULL,
    manufacture_year  int         NOT NULL,
    status            varchar(20) NOT NULL,
    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    CONSTRAINT uq_vehicle_plate UNIQUE (plate_number)
);
-- Moi tai xe mot phuong tien dang dung
CREATE UNIQUE INDEX uq_vehicle_active_driver ON vehicles (driver_id) WHERE status = 'ACTIVE';

CREATE TABLE driver_documents (
    id            uuid PRIMARY KEY,
    driver_id     uuid         NOT NULL REFERENCES driver_profiles (id),
    doc_type      varchar(30)  NOT NULL,
    doc_number    varchar(50)  NOT NULL,
    expires_at    date         NOT NULL,
    status        varchar(20)  NOT NULL,
    storage_name  varchar(100) NOT NULL,
    content_type  varchar(50)  NOT NULL,
    file_size     bigint       NOT NULL,
    sha256        varchar(64)  NOT NULL,
    created_at    timestamptz  NOT NULL
);
CREATE UNIQUE INDEX uq_document_current ON driver_documents (driver_id, doc_type) WHERE status <> 'SUPERSEDED';

CREATE TABLE driver_review_decisions (
    id               uuid PRIMARY KEY,
    driver_id        uuid        NOT NULL REFERENCES driver_profiles (id),
    reviewer_id      uuid        NOT NULL,
    decision         varchar(20) NOT NULL,
    reason           varchar(500),
    profile_version  int         NOT NULL,
    created_at       timestamptz NOT NULL
);
CREATE INDEX idx_review_driver ON driver_review_decisions (driver_id, created_at);
CREATE TRIGGER trg_review_immutable BEFORE UPDATE OR DELETE ON driver_review_decisions
    FOR EACH ROW EXECUTE FUNCTION prevent_mutation();


-- Schema cua location-service: vi tri song nam o Redis GEO; DB chi giu ngu canh tai xe va bang outbox/inbox.

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
-- location
-- =====================================================================
CREATE TABLE driver_contexts (
    driver_id       uuid PRIMARY KEY,
    availability    varchar(20) NOT NULL,
    vehicle_type    varchar(20) NOT NULL,
    source_version  bigint      NOT NULL,
    updated_at      timestamptz NOT NULL
);
CREATE INDEX idx_driver_contexts_availability ON driver_contexts (availability);

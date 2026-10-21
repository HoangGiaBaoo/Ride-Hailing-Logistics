-- Schema cua payment-service: database rieng, khong khoa ngoai sang service khac, chi luu ID.

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

-- Event nhan tu service khac qua Redis Stream: luu truoc, xu ly sau (inbox), de consumer
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
-- payment
-- =====================================================================
CREATE TABLE payments (
    id               uuid PRIMARY KEY,
    trip_id          uuid          NOT NULL,
    kind             varchar(30)   NOT NULL,
    trip_code        varchar(20)   NOT NULL,
    customer_id      uuid          NOT NULL,
    driver_id        uuid,
    service_type     varchar(20)   NOT NULL,
    amount           numeric(15,2) NOT NULL CHECK (amount >= 0),
    refunded_amount  numeric(15,2) NOT NULL DEFAULT 0 CHECK (refunded_amount >= 0),
    currency         varchar(3)    NOT NULL,
    method           varchar(20)   NOT NULL,
    status           varchar(30)   NOT NULL,
    provider         varchar(20)   NOT NULL,
    provider_ref     varchar(64),
    attempts         int           NOT NULL DEFAULT 0,
    failure_reason   varchar(255),
    succeeded_at     timestamptz,
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    version          bigint        NOT NULL DEFAULT 0,
    CONSTRAINT uq_payment_trip_kind UNIQUE (trip_id, kind),
    CONSTRAINT uq_payment_provider_ref UNIQUE (provider_ref),
    CHECK (refunded_amount <= amount)
);
CREATE INDEX idx_payment_customer ON payments (customer_id, created_at DESC);
CREATE INDEX idx_payment_status ON payments (status, created_at DESC);

CREATE TABLE refunds (
    id            uuid PRIMARY KEY,
    payment_id    uuid          NOT NULL REFERENCES payments (id),
    amount        numeric(15,2) NOT NULL CHECK (amount > 0),
    currency      varchar(3)    NOT NULL,
    reason        varchar(500)  NOT NULL,
    requested_by  uuid          NOT NULL,
    status        varchar(20)   NOT NULL,
    provider_ref  varchar(64),
    created_at    timestamptz   NOT NULL
);
CREATE INDEX idx_refund_payment ON refunds (payment_id);

-- Callback da nhan, chong replay (NFR-SEC-010, FR-PAY-005)
CREATE TABLE payment_callback_events (
    event_id     varchar(64) PRIMARY KEY,
    received_at  timestamptz NOT NULL
);

-- =====================================================================
-- wallet
-- =====================================================================
CREATE TABLE wallets (
    id          uuid PRIMARY KEY,
    driver_id   uuid        NOT NULL UNIQUE,
    currency    varchar(3)  NOT NULL,
    status      varchar(20) NOT NULL,
    created_at  timestamptz NOT NULL,
    version     bigint      NOT NULL DEFAULT 0
);

CREATE TABLE wallet_entries (
    id              uuid PRIMARY KEY,
    wallet_id       uuid          NOT NULL REFERENCES wallets (id),
    entry_type      varchar(30)   NOT NULL,
    amount          numeric(15,2) NOT NULL,
    currency        varchar(3)    NOT NULL,
    reference_type  varchar(30)   NOT NULL,
    reference_id    varchar(64)   NOT NULL,
    description     varchar(255),
    status          varchar(20)   NOT NULL,
    created_by      uuid,
    created_at      timestamptz   NOT NULL,
    -- Ghi so idempotent theo nghiep vu (FR-WAL-005)
    CONSTRAINT uq_wallet_entry_ref UNIQUE (wallet_id, entry_type, reference_id)
);
CREATE INDEX idx_wallet_entries ON wallet_entries (wallet_id, created_at DESC);
CREATE TRIGGER trg_wallet_entry_immutable BEFORE UPDATE OR DELETE ON wallet_entries
    FOR EACH ROW EXECUTE FUNCTION prevent_mutation();

CREATE TABLE commission_rules (
    id              uuid PRIMARY KEY,
    service_type    varchar(20)  NOT NULL,
    rate            numeric(5,4) NOT NULL CHECK (rate >= 0 AND rate < 1),
    rule_version    int          NOT NULL,
    effective_from  timestamptz  NOT NULL,
    effective_to    timestamptz,
    created_by      uuid,
    created_at      timestamptz  NOT NULL
);
CREATE INDEX idx_commission_lookup ON commission_rules (service_type, effective_from DESC);

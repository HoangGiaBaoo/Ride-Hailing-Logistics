-- Schema cua pricing-service: bang gia va bao gia. Database rieng, chi luu ID cua service khac.

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
-- pricing
-- =====================================================================
CREATE TABLE pricing_rules (
    id              uuid PRIMARY KEY,
    service_type    varchar(20)   NOT NULL,
    vehicle_type    varchar(20)   NOT NULL,
    region_code     varchar(20)   NOT NULL,
    base_fare       numeric(15,2) NOT NULL CHECK (base_fare >= 0),
    per_km          numeric(15,2) NOT NULL CHECK (per_km >= 0),
    per_minute      numeric(15,2) NOT NULL CHECK (per_minute >= 0),
    minimum_fare    numeric(15,2) NOT NULL CHECK (minimum_fare >= 0),
    booking_fee     numeric(15,2) NOT NULL CHECK (booking_fee >= 0),
    currency        varchar(3)    NOT NULL,
    rule_version    int           NOT NULL,
    effective_from  timestamptz   NOT NULL,
    effective_to    timestamptz,
    active          boolean       NOT NULL,
    created_by      uuid,
    created_at      timestamptz   NOT NULL,
    CHECK (effective_to IS NULL OR effective_to > effective_from)
);
CREATE INDEX idx_pricing_lookup ON pricing_rules (service_type, vehicle_type, region_code) WHERE active;

CREATE TABLE fare_quotes (
    id                uuid PRIMARY KEY,
    customer_id       uuid          NOT NULL,
    service_type      varchar(20)   NOT NULL,
    vehicle_type      varchar(20)   NOT NULL,
    region_code       varchar(20)   NOT NULL,
    pickup_lat        double precision NOT NULL,
    pickup_lng        double precision NOT NULL,
    pickup_address    varchar(255)  NOT NULL,
    dropoff_lat       double precision NOT NULL,
    dropoff_lng       double precision NOT NULL,
    dropoff_address   varchar(255)  NOT NULL,
    distance_meters   int           NOT NULL,
    duration_seconds  int           NOT NULL,
    approximate       boolean       NOT NULL,
    base_fare         numeric(15,2) NOT NULL,
    distance_fare     numeric(15,2) NOT NULL,
    time_fare         numeric(15,2) NOT NULL,
    booking_fee       numeric(15,2) NOT NULL,
    minimum_fare      numeric(15,2) NOT NULL,
    surge_multiplier  numeric(4,2)  NOT NULL CHECK (surge_multiplier >= 1),
    surge_version     varchar(20)   NOT NULL,
    surge_cell        varchar(12)   NOT NULL,
    total             numeric(15,2) NOT NULL CHECK (total >= 0),
    currency          varchar(3)    NOT NULL,
    rule_id           uuid          NOT NULL,
    rule_version      int           NOT NULL,
    status            varchar(20)   NOT NULL,
    expires_at        timestamptz   NOT NULL,
    used_at           timestamptz,
    consumed_by_trip_id uuid,
    created_at        timestamptz   NOT NULL
);
CREATE INDEX idx_quote_customer ON fare_quotes (customer_id, created_at DESC);


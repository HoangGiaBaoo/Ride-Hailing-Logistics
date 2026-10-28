-- Schema cua trip-service: chuyen, lich su trang thai, offer, kha dung tai xe. Database rieng,
-- khong khoa ngoai sang service khac, chi luu ID.


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
-- trip
-- =====================================================================
CREATE TABLE trips (
    id                     uuid PRIMARY KEY,
    code                   varchar(20)   NOT NULL UNIQUE,
    customer_id            uuid          NOT NULL,
    driver_id              uuid,
    vehicle_id             uuid,
    service_type           varchar(20)   NOT NULL,
    vehicle_type           varchar(20)   NOT NULL,
    status                 varchar(20)   NOT NULL,
    pickup_lat             double precision NOT NULL,
    pickup_lng             double precision NOT NULL,
    pickup_address         varchar(255)  NOT NULL,
    dropoff_lat            double precision NOT NULL,
    dropoff_lng            double precision NOT NULL,
    dropoff_address        varchar(255)  NOT NULL,
    quote_id               uuid          NOT NULL UNIQUE,
    distance_meters        int           NOT NULL,
    duration_seconds       int           NOT NULL,
    base_fare              numeric(15,2) NOT NULL,
    distance_fare          numeric(15,2) NOT NULL,
    time_fare              numeric(15,2) NOT NULL,
    booking_fee            numeric(15,2) NOT NULL,
    surge_multiplier       numeric(4,2)  NOT NULL,
    quoted_total           numeric(15,2) NOT NULL,
    currency               varchar(3)    NOT NULL,
    pricing_rule_id        uuid          NOT NULL,
    pricing_rule_version   int           NOT NULL,
    payment_method         varchar(20)   NOT NULL,
    start_pin              varchar(4)    NOT NULL,
    delivery_code          varchar(6),
    recipient_name         varchar(100),
    recipient_phone        varchar(20),
    package_description    varchar(500),
    package_weight_kg      double precision,
    driver_name            varchar(100),
    driver_phone           varchar(20),
    vehicle_plate          varchar(15),
    vehicle_description    varchar(150),
    matching_started_at    timestamptz,
    matching_round         int           NOT NULL DEFAULT 0,
    next_dispatch_at       timestamptz,
    accepted_at            timestamptz,
    arrived_at             timestamptz,
    started_at             timestamptz,
    completed_at           timestamptz,
    ended_at               timestamptz,
    cancelled_by_role      varchar(30),
    cancel_reason_code     varchar(30),
    cancel_note            varchar(500),
    cancellation_fee       numeric(15,2),
    final_fare             numeric(15,2),
    created_at             timestamptz   NOT NULL,
    updated_at             timestamptz   NOT NULL,
    version                bigint        NOT NULL DEFAULT 0
);
-- Bao dam cung trong DB: mot tai xe toi da mot chuyen dang hoat dong (BR-002, NFR-REL-003)
CREATE UNIQUE INDEX uq_trip_active_driver ON trips (driver_id)
    WHERE status IN ('ACCEPTED', 'PICKING_UP', 'ARRIVED', 'IN_TRIP');
-- Mot khach toi da mot chuyen dang hoat dong (FR-TRIP-007)
CREATE UNIQUE INDEX uq_trip_active_customer ON trips (customer_id)
    WHERE status IN ('CREATED', 'MATCHING', 'ACCEPTED', 'PICKING_UP', 'ARRIVED', 'IN_TRIP');
CREATE INDEX idx_trip_customer ON trips (customer_id, created_at DESC);
CREATE INDEX idx_trip_driver ON trips (driver_id, created_at DESC);
CREATE INDEX idx_trip_status ON trips (status, created_at DESC);
CREATE INDEX idx_trip_matching ON trips (next_dispatch_at) WHERE status = 'MATCHING';

CREATE TABLE trip_status_history (
    id           bigserial PRIMARY KEY,
    trip_id      uuid        NOT NULL REFERENCES trips (id),
    from_status  varchar(20),
    to_status    varchar(20) NOT NULL,
    actor_id     uuid,
    actor_role   varchar(30) NOT NULL,
    reason       varchar(500),
    lat          double precision,
    lng          double precision,
    created_at   timestamptz NOT NULL
);
CREATE INDEX idx_trip_history ON trip_status_history (trip_id, id);
CREATE TRIGGER trg_trip_history_immutable BEFORE UPDATE OR DELETE ON trip_status_history
    FOR EACH ROW EXECUTE FUNCTION prevent_mutation();

CREATE TABLE driver_offers (
    id               uuid PRIMARY KEY,
    trip_id          uuid        NOT NULL REFERENCES trips (id),
    driver_id        uuid        NOT NULL,
    status           varchar(20) NOT NULL,
    round            int         NOT NULL,
    distance_meters  int         NOT NULL,
    offered_at       timestamptz NOT NULL,
    expires_at       timestamptz NOT NULL,
    responded_at     timestamptz
);
-- Chi mot offer thang cho moi chuyen; moi tai xe chi giu mot offer cho tai mot thoi diem (FR-MAT-011)
CREATE UNIQUE INDEX uq_offer_accepted_trip ON driver_offers (trip_id) WHERE status = 'ACCEPTED';
CREATE UNIQUE INDEX uq_offer_pending_driver ON driver_offers (driver_id) WHERE status = 'PENDING';
CREATE UNIQUE INDEX uq_offer_pending_trip ON driver_offers (trip_id) WHERE status = 'PENDING';
CREATE INDEX idx_offer_expiry ON driver_offers (expires_at) WHERE status = 'PENDING';
CREATE INDEX idx_offer_trip ON driver_offers (trip_id);

-- =====================================================================
-- kha dung tai xe (SRS §7.2) — nam cung DB voi trips de chap nhan offer la MOT transaction
-- =====================================================================
CREATE TABLE driver_states (
    driver_id                uuid         PRIMARY KEY,
    availability             varchar(20)  NOT NULL,
    suspended                boolean      NOT NULL DEFAULT false,
    account_disabled         boolean      NOT NULL DEFAULT false,
    full_name                varchar(150),
    phone                    varchar(20),
    vehicle_id               uuid,
    vehicle_type             varchar(20),
    plate_number             varchar(20),
    vehicle_description      varchar(200),
    availability_changed_at  timestamptz  NOT NULL,
    version                  bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ck_driver_state_availability CHECK (availability IN ('OFFLINE', 'AVAILABLE', 'OFFERED', 'BUSY'))
);
CREATE INDEX idx_driver_state_available ON driver_states (vehicle_type) WHERE availability = 'AVAILABLE';

-- Schema khoi tao. Moi bang thuoc dung mot mien (CON-02); khong co khoa ngoai xuyen mien,
-- chi luu ID, de sau nay tach service khong phai doi schema.

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

-- =====================================================================
-- identity
-- =====================================================================
CREATE TABLE users (
    id                     uuid PRIMARY KEY,
    email                  varchar(254) NOT NULL,
    phone                  varchar(20)  NOT NULL,
    password_hash          varchar(100) NOT NULL,
    full_name              varchar(100) NOT NULL,
    status                 varchar(20)  NOT NULL,
    failed_login_attempts  int          NOT NULL DEFAULT 0,
    locked_until           timestamptz,
    created_at             timestamptz  NOT NULL,
    updated_at             timestamptz  NOT NULL,
    version                bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_phone UNIQUE (phone)
);

CREATE TABLE user_roles (
    user_id  uuid        NOT NULL REFERENCES users (id),
    role     varchar(30) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE refresh_tokens (
    id           uuid PRIMARY KEY,
    user_id      uuid        NOT NULL REFERENCES users (id),
    token_hash   varchar(64) NOT NULL UNIQUE,
    expires_at   timestamptz NOT NULL,
    revoked_at   timestamptz,
    created_at   timestamptz NOT NULL
);
CREATE INDEX idx_refresh_user ON refresh_tokens (user_id);

CREATE TABLE password_reset_tokens (
    id          uuid PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES users (id),
    token_hash  varchar(64) NOT NULL UNIQUE,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz NOT NULL
);

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
    availability             varchar(20)  NOT NULL,
    availability_changed_at  timestamptz  NOT NULL,
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
    created_at        timestamptz   NOT NULL
);
CREATE INDEX idx_quote_customer ON fare_quotes (customer_id, created_at DESC);

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
-- payment
-- =====================================================================
CREATE TABLE payments (
    id               uuid PRIMARY KEY,
    trip_id          uuid          NOT NULL,
    kind             varchar(30)   NOT NULL,
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

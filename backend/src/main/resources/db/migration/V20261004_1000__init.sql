-- ============ Phase 0 (shared) ============

CREATE TABLE users (
  id                      BIGSERIAL PRIMARY KEY,
  email                   VARCHAR(255) NOT NULL UNIQUE,
  phone                   VARCHAR(20) UNIQUE,
  password_hash           VARCHAR(255) NOT NULL,
  role                    VARCHAR(20)  NOT NULL,   -- DONOR | HOSPITAL | ADMIN
  -- hospital_id is added by ALTER below (circular FK with hospitals.verified_by).
  -- MANY staff users CAN belong to ONE hospital — that is the point of going web:
  -- each staff member logs in on the shared workstation, so verified_by/confirmed_by
  -- identify a person, not a building.
  consent_at              TIMESTAMPTZ NULL,        -- DPDP: general terms consent
  verification_consent_at TIMESTAMPTZ NULL,        -- separate, purpose-specific (§6.1 / T-F14)
  deleted_at              TIMESTAMPTZ NULL,        -- soft delete / right to erasure
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_role ON users (role) WHERE deleted_at IS NULL;

-- ============ VISHRUDH ============

CREATE TABLE hospitals (
  id                  BIGSERIAL PRIMARY KEY,
  name                VARCHAR(150) NOT NULL,
  address             VARCHAR(255) NOT NULL,
  lat                 NUMERIC(9,6) NOT NULL,
  lng                 NUMERIC(9,6) NOT NULL,
  phone               VARCHAR(20)  NOT NULL,
  -- what the admin actually checks before approving (collect at registration):
  license_number      VARCHAR(100) NOT NULL,
  contact_person_name VARCHAR(100) NOT NULL,
  status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | APPROVED | REJECTED
  rejection_reason    VARCHAR(255) NULL,           -- V-B2 says "reject with reason" — this holds it
  verified_by         BIGINT NULL REFERENCES users(id),         -- admin user id
  verified_at         TIMESTAMPTZ NULL,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_hospitals_status ON hospitals (status);

ALTER TABLE users
  ADD COLUMN hospital_id BIGINT NULL REFERENCES hospitals(id);
CREATE INDEX idx_users_hospital ON users (hospital_id);

-- Optional but recommended — fixes the "hospital records fulfillment" design flaw.
-- Plain table in v1: no centre login, no centre app (§11).
CREATE TABLE blood_centres (
  id      BIGSERIAL PRIMARY KEY,
  name    VARCHAR(150) NOT NULL,
  address VARCHAR(255) NOT NULL,
  lat     NUMERIC(9,6) NOT NULL,
  lng     NUMERIC(9,6) NOT NULL,
  phone   VARCHAR(20) NULL
);

-- ============ THIRU ============

CREATE TABLE donors (
  id                      BIGSERIAL PRIMARY KEY,
  user_id                 BIGINT NOT NULL UNIQUE REFERENCES users(id),
  name                    VARCHAR(100) NOT NULL,
  blood_group             VARCHAR(10)  NOT NULL,   -- A_POS A_NEG B_POS B_NEG AB_POS AB_NEG O_POS O_NEG
  gender                  VARCHAR(10)  NOT NULL,   -- MALE | FEMALE | OTHER
  date_of_birth           DATE NOT NULL,
  -- location: set by map pin, stored at ~1km precision only (§5.3).
  -- No street address, no geocoding API.
  lat                     NUMERIC(9,6) NOT NULL,
  lng                     NUMERIC(9,6) NOT NULL,
  area_label              VARCHAR(100) NULL,       -- coarse display label, e.g. "Anna Nagar, Chennai"
  address_confirmed_at    TIMESTAMPTZ NULL,        -- set on BOTH answers; "no change" is data too
  last_donation_date      DATE NULL,               -- authoritative once a centre confirms (V-B8a)
  is_available            BOOLEAN NOT NULL DEFAULT TRUE,
  reliability_score       NUMERIC(3,2) NOT NULL DEFAULT 0.50,  -- Vishrudh's side updates from events
  fcm_token               VARCHAR(255) NULL,
  -- donor alert preferences (T-F11)
  call_alerts_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
  quiet_hours_start       TIME NULL,
  quiet_hours_end         TIME NULL,
  -- blood group verification (§6.1). Written ONLY by a centre/hospital account at confirm-donated.
  blood_group_verified    BOOLEAN NOT NULL DEFAULT FALSE,
  blood_group_verified_at TIMESTAMPTZ NULL,
  blood_group_verified_by BIGINT NULL REFERENCES users(id),  -- the staff user who saw the lab result
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Powers the eligibility pre-filter + bounding box (§3.2)
CREATE INDEX idx_donor_search ON donors (blood_group, is_available, lat, lng);
CREATE INDEX idx_donor_bbox   ON donors (lat, lng);

-- ============ VISHRUDH ============

CREATE TABLE blood_requests (
  id              BIGSERIAL PRIMARY KEY,
  hospital_id     BIGINT NOT NULL REFERENCES hospitals(id),
  blood_centre_id BIGINT NULL REFERENCES blood_centres(id),
  created_by      BIGINT NOT NULL REFERENCES users(id),  -- which staff member raised it
  blood_group     VARCHAR(10) NOT NULL,
  component       VARCHAR(20) NOT NULL DEFAULT 'WHOLE_BLOOD',  -- WHOLE_BLOOD | SDP | PLASMA
  units_needed    INT NOT NULL CHECK (units_needed > 0),
  urgency         VARCHAR(10) NOT NULL,   -- LOW | MEDIUM | HIGH | CRITICAL
  status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
                  -- OPEN | IN_PROGRESS | FULFILLED | EXPIRED | CANCELLED
  cancel_reason   VARCHAR(255) NULL,      -- e.g. "sourced elsewhere"
  current_wave    INT NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at      TIMESTAMPTZ NULL
);
CREATE INDEX idx_hospital_requests ON blood_requests (hospital_id, status);
CREATE INDEX idx_requests_open     ON blood_requests (status) WHERE status IN ('OPEN','IN_PROGRESS');

-- Append-only request lifecycle log. Master doc §11: every state change is logged.
CREATE TABLE request_events (
  id             BIGSERIAL PRIMARY KEY,
  request_id     BIGINT NOT NULL REFERENCES blood_requests(id),
  event_type     VARCHAR(40) NOT NULL,
                 -- CREATED, WAVE_STARTED, WAVE_TIMEOUT, REOPENED, FULFILLED,
                 -- CANCELLED, EXPIRED
  actor_user_id  BIGINT NULL REFERENCES users(id),   -- NULL when the scheduler did it
  event_time     TIMESTAMPTZ NOT NULL DEFAULT now(),
  metadata_json  JSONB NULL
);
CREATE INDEX idx_request_events ON request_events (request_id, event_time);

-- The per-donor-per-request state machine. SINGLE OWNER: Vishrudh (fix #3).
CREATE TABLE donor_notifications (
  id             BIGSERIAL PRIMARY KEY,
  request_id     BIGINT NOT NULL REFERENCES blood_requests(id),
  donor_id       BIGINT NOT NULL REFERENCES donors(id),
  wave_number    INT NOT NULL,
  channel        VARCHAR(20) NOT NULL DEFAULT 'PUSH',   -- PUSH | SMS | WHATSAPP
  score          NUMERIC(4,3) NOT NULL,                 -- ranking score snapshot at send time
  distance_km    NUMERIC(6,2) NOT NULL,                 -- distance snapshot at send time
  status         VARCHAR(20) NOT NULL DEFAULT 'NOTIFIED',
                 -- NOTIFIED | ACCEPTED | DECLINED | WITHDRAWN
                 -- | NO_RESPONSE | EN_ROUTE | DONATED | EXPIRED
  sent_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  responded_at   TIMESTAMPTZ NULL,
  donated_at     TIMESTAMPTZ NULL,
  confirmed_by   BIGINT NULL REFERENCES users(id),      -- staff user who confirmed DONATED
  CONSTRAINT uq_request_donor_wave UNIQUE (request_id, donor_id, wave_number)
);
CREATE INDEX idx_notif_request  ON donor_notifications (request_id, status);
CREATE INDEX idx_notif_donor    ON donor_notifications (donor_id, sent_at);  -- weekly fatigue lookup
CREATE INDEX idx_notif_active   ON donor_notifications (status) WHERE status IN ('NOTIFIED','ACCEPTED','EN_ROUTE');

-- Append-only audit log (master doc §8). Feeds reliability scoring and Phase 7 analytics.
CREATE TABLE notification_events (
  id                    BIGSERIAL PRIMARY KEY,
  donor_notification_id BIGINT NOT NULL REFERENCES donor_notifications(id),
  event_type            VARCHAR(30) NOT NULL,
                        -- SENT, DELIVERED, OPENED, ACCEPTED, DECLINED, WITHDRAWN,
                        -- EN_ROUTE, DONATED, TIMEOUT
  event_time            TIMESTAMPTZ NOT NULL DEFAULT now(),
  metadata_json         JSONB NULL
);
CREATE INDEX idx_notification_events ON notification_events (donor_notification_id, event_time);

-- Verification history + audit trail + source of the mismatch-rate metric (§6.1).
-- Never updated in place: one row per verification event, so a bad entry is superseded
-- by a later one rather than welded in permanently (guardrail 2).
CREATE TABLE blood_group_verifications (
  id                    BIGSERIAL PRIMARY KEY,
  donor_id              BIGINT NOT NULL REFERENCES donors(id),
  donor_notification_id BIGINT NULL REFERENCES donor_notifications(id),  -- which donation
  declared_group        VARCHAR(10) NOT NULL,
  observed_group        VARCHAR(10) NOT NULL,
  matched               BOOLEAN NOT NULL,
  verified_by           BIGINT NOT NULL REFERENCES users(id),
  disputed_at           TIMESTAMPTZ NULL,   -- donor used the dispute path (T-F13)
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_donor_verifications ON blood_group_verifications (donor_id, created_at);

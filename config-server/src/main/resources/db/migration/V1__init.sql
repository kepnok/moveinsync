CREATE TABLE config_head (
  id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  version BIGINT NOT NULL DEFAULT 0,
  epoch UUID NOT NULL DEFAULT gen_random_uuid()
);
INSERT INTO config_head DEFAULT VALUES;

CREATE TABLE setting_definitions (
  setting_key VARCHAR(128) PRIMARY KEY,
  value_type VARCHAR(16) NOT NULL,
  default_value JSONB NOT NULL,
  rules JSONB NOT NULL DEFAULT '{}',
  description TEXT,
  updated_version BIGINT NOT NULL
);

CREATE TABLE overrides (
  setting_key VARCHAR(128) NOT NULL REFERENCES setting_definitions(setting_key),
  scope_type VARCHAR(8) NOT NULL,
  scope_id VARCHAR(256) NOT NULL,
  tenant_id VARCHAR(64),
  value JSONB NOT NULL,
  updated_version BIGINT NOT NULL,
  PRIMARY KEY (setting_key, scope_type, scope_id)
);
CREATE INDEX idx_overrides_tenant ON overrides(tenant_id);

CREATE TABLE change_sets (
  version BIGINT PRIMARY KEY,
  change_set_id UUID NOT NULL UNIQUE,
  kind VARCHAR(24) NOT NULL,
  author_id VARCHAR(64) NOT NULL,
  reason TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  idempotency_key VARCHAR(64) UNIQUE,
  rollout_id UUID,
  rollback_to BIGINT
);

CREATE TABLE change_items (
  id BIGSERIAL PRIMARY KEY,
  version BIGINT NOT NULL REFERENCES change_sets(version),
  item_type VARCHAR(12) NOT NULL,
  setting_key VARCHAR(128) NOT NULL,
  scope_type VARCHAR(8),
  scope_id VARCHAR(256),
  tenant_id VARCHAR(64),
  old_value JSONB,
  new_value JSONB
);
CREATE INDEX idx_items_version ON change_items(version);

CREATE TABLE outbox (
  id BIGSERIAL PRIMARY KEY,
  version BIGINT NOT NULL UNIQUE,
  tenants TEXT[] NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending ON outbox(version) WHERE published_at IS NULL;

CREATE TABLE rollouts (
  id UUID PRIMARY KEY,
  status VARCHAR(12) NOT NULL,
  tenant_id VARCHAR(64) NOT NULL,
  stage_version BIGINT NOT NULL REFERENCES change_sets(version),
  final_version BIGINT REFERENCES change_sets(version),
  created_by VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE rollout_targets (
  rollout_id UUID REFERENCES rollouts(id),
  office_scope_id VARCHAR(256),
  PRIMARY KEY (rollout_id, office_scope_id)
);

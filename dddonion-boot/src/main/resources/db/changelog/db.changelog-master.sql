-- liquibase formatted sql

-- changeset dddonion:001-create-orders
CREATE TABLE IF NOT EXISTS orders (
  id VARCHAR(64) PRIMARY KEY,
  customer_email VARCHAR(255) NOT NULL,
  total DECIMAL(19,2) NOT NULL,
  status VARCHAR(32) NOT NULL,
  version BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- changeset dddonion:002-create-outbox
CREATE TABLE IF NOT EXISTS outbox_events (
  id VARCHAR(64) PRIMARY KEY,
  aggregate_id VARCHAR(64) NOT NULL,
  sequence BIGINT NOT NULL,
  type VARCHAR(128) NOT NULL,
  occurred_at TIMESTAMP NOT NULL,
  payload_json LONGTEXT NOT NULL,
  published BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY ux_agg_seq (aggregate_id, sequence)
);

-- changeset dddonion:003-indexes
CREATE INDEX idx_outbox_published_agg_seq ON outbox_events (published, aggregate_id, sequence);
CREATE INDEX idx_outbox_occurred_at ON outbox_events (occurred_at);

-- changeset dddonion:004-checks
ALTER TABLE orders
  ADD CONSTRAINT chk_orders_total_nonneg CHECK (total >= 0);
ALTER TABLE orders
  ADD CONSTRAINT chk_orders_status_enum CHECK (status IN ('NEW','PLACED','PAID'));

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

-- changeset dddonion:004-checks
ALTER TABLE orders
  ADD CONSTRAINT chk_orders_total_nonneg CHECK (total >= 0);
ALTER TABLE orders
  ADD CONSTRAINT chk_orders_status_enum CHECK (status IN ('NEW','PLACED','PAID'));

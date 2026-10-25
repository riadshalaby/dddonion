-- liquibase formatted sql

-- changeset dddonion:001-create-schema
CREATE TABLE IF NOT EXISTS orders (
  id VARCHAR(64) PRIMARY KEY,
  customer_id VARCHAR(36) NOT NULL,
  total DECIMAL(19,2) NOT NULL,
  status VARCHAR(32) NOT NULL,
  version BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS customers (
  id VARCHAR(36) PRIMARY KEY,
  email VARCHAR(254) NOT NULL,
  credit_limit DECIMAL(19,2) NOT NULL,
  open_amount DECIMAL(19,2) NOT NULL,
  version BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_customers_email UNIQUE (email)
);

-- changeset dddonion:004-checks
ALTER TABLE orders
  ADD CONSTRAINT chk_orders_total_nonneg CHECK (total >= 0);
ALTER TABLE orders
  ADD CONSTRAINT chk_orders_status_enum CHECK (status IN ('NEW','PLACED','PAID'));
ALTER TABLE customers
  ADD CONSTRAINT chk_customers_credit_limit_nonneg CHECK (credit_limit >= 0);
ALTER TABLE customers
  ADD CONSTRAINT chk_customers_open_amount_nonneg CHECK (open_amount >= 0);

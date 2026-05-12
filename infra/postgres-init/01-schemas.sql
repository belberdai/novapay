-- Runs once on first container startup (Postgres init convention).
-- Flyway will own table-level migrations from inside the Spring Boot app;
-- this file just sets up the schemas Flyway writes into.

CREATE SCHEMA IF NOT EXISTS payment;
CREATE SCHEMA IF NOT EXISTS outbox;

-- Default search path so Flyway and queries don't need to qualify everything.
ALTER DATABASE payments SET search_path TO payment, outbox, public;

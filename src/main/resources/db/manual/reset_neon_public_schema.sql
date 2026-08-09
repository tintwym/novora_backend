-- =============================================================================
-- Neon / PostgreSQL: FULL RESET of the `public` schema (all tables + data).
-- =============================================================================
-- When to use: dev branch only — you want an empty DB and will let the Spring app
-- recreate tables (spring.jpa.hibernate.ddl-auto=update) OR you will run V10 SQL after.
--
-- How to run:
--   1) Stop your Spring Boot app (releases DB connections).
--   2) Neon Dashboard → SQL Editor → select this database → paste and execute.
--   3) Start the app again (Hibernate `update` will create tables from entities),
--      OR run db/migration/V10__novora_schema_v1_1.sql from the top if you want SQL-defined schema.
--
-- WARNING: This deletes every table, view, sequence, and extension object in `public`.
-- =============================================================================

DROP SCHEMA IF EXISTS public CASCADE;

CREATE SCHEMA public;

GRANT ALL ON SCHEMA public TO PUBLIC;

-- V10 migration and some entities rely on uuid_generate_v4().
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;

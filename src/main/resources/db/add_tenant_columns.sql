-- ============================================================================
-- Recuperación: crea las columnas laboratory_id (@TenantId) que Hibernate
-- ddl-auto NO agregó en el despliegue.
-- ============================================================================
--
-- Síntoma: el código con @TenantId está desplegado y filtra por laboratory_id,
-- pero las columnas no existen en la BD -> toda consulta a customer/parameter/
-- tests/etc. responde 500 ("column laboratory_id does not exist").
--
-- Este script agrega esas columnas de forma idempotente. Es lo mismo que haría
-- ddl-auto=update. No asigna valores (quedan NULL); para poblarlas correr después
-- backfill_tenant.sql.
--
-- Ejecutar UNA vez:
--     psql "$DATABASE_PUBLIC_URL" -f src/main/resources/db/add_tenant_columns.sql
--
-- Orden completo de recuperación:
--     1) add_tenant_columns.sql   (crea columnas -> se detienen los 500)
--     2) backfill_tenant.sql      (asigna el laboratorio a las filas existentes -> reaparecen los datos)
--     3) backfill_order_numbers.sql (folios de las órdenes)
-- ============================================================================

ALTER TABLE IF EXISTS customer         ADD COLUMN IF NOT EXISTS laboratory_id bigint;
ALTER TABLE IF EXISTS parameter        ADD COLUMN IF NOT EXISTS laboratory_id bigint;
ALTER TABLE IF EXISTS age_range        ADD COLUMN IF NOT EXISTS laboratory_id bigint;
ALTER TABLE IF EXISTS pathology        ADD COLUMN IF NOT EXISTS laboratory_id bigint;
ALTER TABLE IF EXISTS unit             ADD COLUMN IF NOT EXISTS laboratory_id bigint;
ALTER TABLE IF EXISTS tests            ADD COLUMN IF NOT EXISTS laboratory_id bigint;
ALTER TABLE IF EXISTS test_config      ADD COLUMN IF NOT EXISTS laboratory_id bigint;
ALTER TABLE IF EXISTS reference_range  ADD COLUMN IF NOT EXISTS laboratory_id bigint;
ALTER TABLE IF EXISTS lab_tests        ADD COLUMN IF NOT EXISTS laboratory_id bigint;
ALTER TABLE IF EXISTS test_runs        ADD COLUMN IF NOT EXISTS laboratory_id bigint;
ALTER TABLE IF EXISTS test_results     ADD COLUMN IF NOT EXISTS laboratory_id bigint;
-- lab_orders.laboratory_id ya existe (venía de la FK del folio).

-- Verificación: todas deberían dar 1.
--   SELECT table_name, count(*) FILTER (WHERE column_name='laboratory_id')
--   FROM information_schema.columns
--   WHERE table_name IN ('customer','parameter','age_range','pathology','unit','tests',
--                        'test_config','reference_range','lab_tests','test_runs','test_results')
--   GROUP BY table_name ORDER BY table_name;

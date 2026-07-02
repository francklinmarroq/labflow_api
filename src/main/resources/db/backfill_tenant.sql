-- ============================================================================
-- Backfill de laboratory_id (tenant) para datos existentes.
-- ============================================================================
--
-- Al introducir la multi-tenancy con @TenantId, cada tabla de tenant gana una
-- columna laboratory_id. Las filas creadas antes quedan con laboratory_id NULL
-- y, como Hibernate filtra por tenant, dejarían de verse. Este script las asigna
-- al laboratorio existente.
--
-- Es de UN SOLO USO. Ejecutar UNA vez, después de arrancar la app al menos una
-- vez (para que Hibernate haya creado las columnas laboratory_id):
--
--     psql "$DB_URL" -f src/main/resources/db/backfill_tenant.sql
--
-- Luego correr backfill_order_numbers.sql para el folio de las órdenes.
--
-- Asume UN SOLO laboratorio: si hay más de uno, aborta (no se puede inferir a
-- cuál pertenece cada fila). Es idempotente: solo toca filas con laboratory_id NULL.
-- ============================================================================

BEGIN;

DO $$
DECLARE
    lab_count  integer;
    target_lab bigint;
    t          text;
    tenant_tables text[] := ARRAY[
        'customer', 'parameter', 'age_range', 'pathology', 'unit',
        'tests', 'test_config', 'reference_range',
        'lab_orders', 'lab_tests', 'test_runs', 'test_results'
    ];
BEGIN
    SELECT count(*) INTO lab_count FROM laboratory;

    IF lab_count = 0 THEN
        RAISE EXCEPTION 'No existe ningún laboratorio. Registrá una cuenta primero.';
    ELSIF lab_count > 1 THEN
        RAISE EXCEPTION 'Hay % laboratorios: no se puede inferir el tenant de las filas legadas. Asigná laboratory_id manualmente.', lab_count;
    END IF;

    SELECT id INTO target_lab FROM laboratory ORDER BY id LIMIT 1;

    FOREACH t IN ARRAY tenant_tables LOOP
        -- Solo tablas que existan y tengan la columna (por si alguna no aplica).
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = t AND column_name = 'laboratory_id'
        ) THEN
            EXECUTE format('UPDATE %I SET laboratory_id = $1 WHERE laboratory_id IS NULL', t)
                USING target_lab;
            RAISE NOTICE 'Backfill % -> laboratorio %', t, target_lab;
        ELSE
            RAISE NOTICE 'Se omite % (sin columna laboratory_id)', t;
        END IF;
    END LOOP;
END $$;

COMMIT;

-- Verificación (opcional): ninguna de estas debería devolver filas con NULL.
--   SELECT 'customer' t, count(*) FROM customer WHERE laboratory_id IS NULL
--   UNION ALL SELECT 'parameter', count(*) FROM parameter WHERE laboratory_id IS NULL
--   UNION ALL SELECT 'tests', count(*) FROM tests WHERE laboratory_id IS NULL;

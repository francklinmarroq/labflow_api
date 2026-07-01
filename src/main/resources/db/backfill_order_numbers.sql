-- ============================================================================
-- Backfill de folios (order_number) por laboratorio para órdenes existentes.
-- ============================================================================
--
-- Contexto: hasta ahora las órdenes usaban el `id` autoincremental como número
-- visible. Al introducir el folio por laboratorio (order_number) las órdenes
-- previas quedan con laboratory_id y order_number en NULL. Este script:
--
--   1. Asigna el laboratorio a las órdenes legadas (asume UN solo laboratorio).
--   2. Numera esas órdenes por laboratorio (1, 2, 3…) ordenadas por id.
--   3. Inicializa el contador lab_order_counters con el siguiente folio.
--
-- Es de UN SOLO USO. Ejecutar UNA vez, después de arrancar la app al menos una
-- vez (para que Hibernate haya creado las columnas/tablas nuevas):
--
--     psql "$DB_URL" -f src/main/resources/db/backfill_order_numbers.sql
--
-- Es idempotente y seguro de re-ejecutar: solo toca filas con order_number NULL
-- y el contador nunca retrocede.
--
-- IMPORTANTE (multi-laboratorio): si en la BD hay MÁS de un laboratorio, no se
-- puede saber a cuál pertenece cada orden legada, así que el paso 1 se ABORTA
-- con un mensaje. En ese caso asigná laboratory_id manualmente y volvé a correr.
-- ============================================================================

BEGIN;

-- Paso 1: asignar laboratorio a las órdenes legadas.
DO $$
DECLARE
    lab_count   integer;
    legacy_count integer;
    target_lab  bigint;
BEGIN
    SELECT count(*) INTO legacy_count FROM lab_orders WHERE laboratory_id IS NULL;

    IF legacy_count = 0 THEN
        RAISE NOTICE 'No hay órdenes sin laboratorio; se omite el paso 1.';
    ELSE
        SELECT count(*) INTO lab_count FROM laboratory;

        IF lab_count = 0 THEN
            RAISE EXCEPTION 'Hay % órdenes sin laboratorio pero no existe ningún laboratorio. Creá el laboratorio primero.', legacy_count;
        ELSIF lab_count > 1 THEN
            RAISE EXCEPTION 'Hay % órdenes sin laboratorio y % laboratorios: no se puede inferir a cuál pertenecen. Asigná laboratory_id manualmente y volvé a correr.', legacy_count, lab_count;
        END IF;

        SELECT id INTO target_lab FROM laboratory ORDER BY id LIMIT 1;
        UPDATE lab_orders SET laboratory_id = target_lab WHERE laboratory_id IS NULL;
        RAISE NOTICE 'Asignadas % órdenes al laboratorio %.', legacy_count, target_lab;
    END IF;
END $$;

-- Paso 2: numerar (order_number) las órdenes sin folio, por laboratorio,
-- continuando después del folio máximo que ya exista en ese laboratorio.
WITH base AS (
    SELECT laboratory_id, COALESCE(MAX(order_number), 0) AS max_num
    FROM lab_orders
    WHERE order_number IS NOT NULL
    GROUP BY laboratory_id
),
numbered AS (
    SELECT o.id,
           o.laboratory_id,
           row_number() OVER (PARTITION BY o.laboratory_id ORDER BY o.id) AS rn
    FROM lab_orders o
    WHERE o.order_number IS NULL
      AND o.laboratory_id IS NOT NULL
)
UPDATE lab_orders o
SET order_number = COALESCE(b.max_num, 0) + n.rn
FROM numbered n
LEFT JOIN base b ON b.laboratory_id = n.laboratory_id
WHERE o.id = n.id;

-- Paso 3: sembrar / actualizar el contador con el siguiente folio de cada lab.
INSERT INTO lab_order_counters (laboratory_id, next_number)
SELECT laboratory_id, MAX(order_number) + 1
FROM lab_orders
WHERE laboratory_id IS NOT NULL
  AND order_number IS NOT NULL
GROUP BY laboratory_id
ON CONFLICT (laboratory_id) DO UPDATE
SET next_number = GREATEST(lab_order_counters.next_number, EXCLUDED.next_number);

COMMIT;

-- Verificación rápida (opcional):
--   SELECT laboratory_id, min(order_number), max(order_number), count(*)
--   FROM lab_orders GROUP BY laboratory_id ORDER BY laboratory_id;
--   SELECT * FROM lab_order_counters ORDER BY laboratory_id;

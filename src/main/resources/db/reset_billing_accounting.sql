-- Reinicia facturación y contabilidad para volver a probar desde cero.
--
-- DESTRUCTIVO y solo para entornos de PRUEBA: borra TODAS las facturas, pagos,
-- gastos y partidas contables de la base. Las órdenes quedan intactas y vuelven
-- a poder facturarse. El catálogo de cuentas (accounts) NO se toca: sus saldos
-- se derivan de las partidas, así que al vaciarlas todo queda en cero.
--
-- No se ejecuta solo: hay que correrlo a mano contra la base (psql / cliente).
-- Va todo en una transacción; si algo falla, no se aplica nada.

begin;

-- Contabilidad: primero las líneas (dependen de partidas y cuentas), luego las partidas.
delete from journal_lines;
delete from journal_entries;

-- Facturación: pagos e ítems (dependen de la factura) antes que las facturas.
delete from invoice_payments;
delete from invoice_items;
delete from invoices;

-- Gastos (dependen de las cuentas, pero no de facturas/partidas).
delete from expenses;

-- Correlativos: que recibos y partidas vuelvan a numerar desde 1.
update payment_counters set next_number = 1;
update journal_entry_counters set next_number = 1;

-- Numeración CAI: dejar el "número actual" en NULL hace que la próxima factura
-- vuelva a arrancar en el "rango desde" configurado.
update laboratory set cai1_current_number = null, cai2_current_number = null;

commit;

-- Opcionales (descomentar si de verdad se quiere):
--
-- Borrar también el catálogo de cuentas (se vuelve a sembrar solo al emitir la
-- próxima factura). Solo si además se quieren descartar cuentas creadas a mano:
--   delete from accounts;
--
-- Limitar a un laboratorio en vez de vaciar toda la base (multi-tenant): agregar
--   where laboratory_id = <ID>
-- a cada delete/update de arriba.

create table if not exists users(username varchar(255) not null primary key,password varchar(500) not null,enabled boolean not null);
create table if not exists authorities (username varchar(255) not null,authority varchar(50) not null,constraint fk_authorities_users foreign key(username) references users(username));
create unique index if not exists ix_auth_username on authorities (username,authority);

-- Si la tabla ya existe (creada antes con varchar(50)), el "create table if not exists"
-- de arriba NO la modifica. Estos ALTER sí amplian la columna y son idempotentes en Postgres.
alter table users alter column username type varchar(255);
alter table authorities alter column username type varchar(255);

-- Limpieza: la columna tests.notes quedó huérfana de una versión previa (la entidad
-- Test ya no la tiene). Se elimina de forma idempotente; "if exists" evita fallar en
-- BD nuevas donde la tabla/columna aún no existen.
alter table if exists tests drop column if exists notes;

-- Hibernate genera un check constraint con la lista de valores del enum Permission
-- en app_role_permission.permission. Con ddl-auto=update ese check NO se actualiza al
-- agregar permisos nuevos (facturación, contabilidad, ajustes del laboratorio), así
-- que guardar un rol con uno de esos permisos falla con "violates check constraint".
-- La validez ya la garantiza el enum de Java, así que el check sobra: se elimina.
-- El check inline sin nombre lo nombra Postgres como <tabla>_<columna>_check. Se usa
-- una sola sentencia (no un bloque DO) a propósito: Spring parte schema.sql por cada
-- ";" y no entiende el dollar-quoting $$, así que un bloque PL/pgSQL rompe el arranque.
-- "if exists" en tabla y constraint lo hace idempotente y seguro en BD nuevas.
alter table if exists app_role_permission drop constraint if exists app_role_permission_permission_check;

-- Mismo caso con otras columnas @Enumerated(STRING) a las que se les agregaron
-- valores nuevos: el módulo de remisiones sumó cuentas de sistema
-- (CUENTAS_POR_PAGAR, EXAMENES_REMITIDOS) y fuentes de partida (REMISION,
-- ANULACION_REMISION). Se eliminan sus check constraints viejos por el mismo
-- motivo; el enum de Java sigue garantizando la validez. Sentencias sueltas
-- (no bloques PL/pgSQL) para no romper el arranque, ver arriba.
alter table if exists accounts drop constraint if exists accounts_system_key_check;
alter table if exists journal_entries drop constraint if exists journal_entries_source_type_check;

-- Enlace público de resultados: la columna lab_orders.public_token la declara la
-- entidad LabOrder, pero ddl-auto=update no siempre la aplica sobre bases ya
-- existentes; sin ella, TODA consulta a lab_orders (que ahora mapea la columna)
-- revienta con "no existe la columna public_token". Se agrega aquí de forma
-- idempotente, que es el mecanismo confiable en este proyecto. "if exists" en la
-- tabla evita fallar en una BD nueva (Hibernate crea la tabla con la columna a
-- partir de la entidad). El backfill de tokens en filas viejas lo hace
-- PublicTokenBackfill al arrancar.
alter table if exists lab_orders add column if not exists public_token varchar(36);
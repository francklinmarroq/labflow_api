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

-- Drift de restricciones UNIQUE al migrar a multi-tenant (commit dc5c260): estas
-- entidades tenían un unique GLOBAL en "name" (@Column(unique=true)) que se cambió
-- por uno compuesto por laboratorio (laboratory_id, name). Pero ddl-auto=update
-- AGREGA la restricción nueva y NUNCA elimina la vieja, así que en bases que
-- existían antes del multi-tenant sobrevive el unique global. Efecto: al registrar
-- un laboratorio NUEVO, el sembrado del catálogo (CatalogSeeder) intenta insertar
-- un examen/patología/rango con un "name" que otro laboratorio ya tiene y revienta
-- con "violates unique constraint" -> 409 "Ya existe un registro con esos datos".
-- El primer laboratorio funciona porque las tablas están vacías; a partir del
-- segundo, falla. Se eliminan las restricciones globales viejas; la validez por
-- laboratorio la garantizan las restricciones uk_*_name_per_lab de las entidades.
-- Los nombres son los que genera Hibernate para @Column(unique=true) (hash
-- determinístico de tabla+columna, iguales en todas las bases). "if exists" en
-- tabla y constraint lo hace idempotente y seguro en bases nuevas (donde nunca
-- existió el unique global).
alter table if exists tests drop constraint if exists uklynqts1dwsv4wxtgh0jfbyeaa;
alter table if exists pathology drop constraint if exists uklby58lspcse6fgiy34dk9kgrp;
alter table if exists age_range drop constraint if exists ukqql5fyaatfi5srbbc3vex478n;
alter table if exists test_config drop constraint if exists ukbqi8wvsabhl626um5wpbognna;

-- Enlace público de resultados: la columna lab_orders.public_token la declara la
-- entidad LabOrder, pero ddl-auto=update no siempre la aplica sobre bases ya
-- existentes; sin ella, TODA consulta a lab_orders (que ahora mapea la columna)
-- revienta con "no existe la columna public_token". Se agrega aquí de forma
-- idempotente, que es el mecanismo confiable en este proyecto. "if exists" en la
-- tabla evita fallar en una BD nueva (Hibernate crea la tabla con la columna a
-- partir de la entidad). El backfill de tokens en filas viejas lo hace
-- PublicTokenBackfill al arrancar.
alter table if exists lab_orders add column if not exists public_token varchar(36);

-- Sello del regente: misma historia que public_token, ddl-auto=update no agrega la
-- columna en bases existentes. Guarda la llave del objeto en el bucket privado (no
-- una URL), igual que logo_object_key.
alter table if exists laboratory add column if not exists stamp_object_key varchar(255);

-- Identidad fiscal del emisor (nombre/razón social y dirección fiscal), distinta
-- del nombre y dirección comercial. Mismo motivo que arriba: ddl-auto=update no
-- siempre agrega columnas nuevas en bases existentes. La factura congela una copia
-- (invoices.lab_tax_name / lab_tax_address) al emitir.
alter table if exists laboratory add column if not exists tax_name varchar(255);
alter table if exists laboratory add column if not exists tax_address varchar(255);
alter table if exists invoices add column if not exists lab_tax_name varchar(255);
alter table if exists invoices add column if not exists lab_tax_address varchar(255);

-- Onboarding de datos de inicio: laboratory.seed_status guarda si el laboratorio
-- ya decidió cargar (o no) el catálogo por defecto. Reemplaza al sembrado en el
-- registro (que corría con el tenant equivocado). Se agrega idempotente; los labs
-- que YA tienen exámenes se dan por decididos (ACCEPTED) para no mostrarles el
-- modal, y los que quedaron vacíos (incluidos los rotos por el bug anterior) se
-- dejan en PENDING para que el modal les ofrezca sembrar ahora. Se trata null
-- como PENDING en el código, así que el orden respecto a ddl-auto no importa.
alter table if exists laboratory add column if not exists seed_status varchar(20);
update laboratory set seed_status = 'ACCEPTED'
  where seed_status is null and id in (select distinct laboratory_id from tests where laboratory_id > 0);
update laboratory set seed_status = 'PENDING' where seed_status is null;
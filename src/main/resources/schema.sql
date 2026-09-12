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

-- Y lo mismo con tests.area (enum TestArea) al agregar el área INMUNOLOGIA: sin
-- esto, guardar un examen de esa área falla con "violates check constraint".
alter table if exists tests drop constraint if exists tests_area_check;

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

-- Mismo drift en customer: national_id_number y tax_number eran @Column(unique=true)
-- (unique GLOBAL) antes del multi-tenant; ahora la unicidad es por laboratorio
-- (uk_customer_national_id_per_lab / uk_customer_tax_number_per_lab). El unique global
-- viejo sobrevive en bases previas y hace que registrar un paciente con un DNI/NIT que
-- otro laboratorio ya usa reviente con "violates unique constraint" -> 409. Se eliminan.
-- Nombres deterministas que genera Hibernate para @Column(unique=true) (hash de
-- tabla+columna). "if exists" en tabla y constraint lo hace idempotente en bases nuevas.
alter table if exists customer drop constraint if exists ukfgxric7ry73qwno3m6rfn3b6i;
alter table if exists customer drop constraint if exists uk9qjuu5myqtv2ojkfcoonu931a;

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

-- Rangos por edad y por contexto fisiológico (fase de ciclo, gestación, menopausia):
-- estas columnas las declara la entidad ReferenceRange, pero ddl-auto=update no las
-- agrega sobre una BD que ya tenía la tabla, y con esas columnas mapeadas TODA consulta
-- a reference_range revienta con "no existe la columna ...". Efecto: en producción no
-- aparece ningún valor de referencia al registrar/imprimir resultados (el frontend se
-- traga el error). Se agregan idempotentes. Las NOT NULL llevan default para que las
-- filas existentes queden como rango común (context_kind='NONE', exclusividad false).
-- "if exists" evita fallar en BD nuevas (Hibernate crea la tabla completa).
alter table if exists reference_range add column if not exists min_age_days integer;
alter table if exists reference_range add column if not exists max_age_days integer;
alter table if exists reference_range add column if not exists lower_exclusive boolean not null default false;
alter table if exists reference_range add column if not exists upper_exclusive boolean not null default false;
alter table if exists reference_range add column if not exists critical_low numeric(38,2);
alter table if exists reference_range add column if not exists critical_high numeric(38,2);
alter table if exists reference_range add column if not exists interpretation_text varchar(255);
alter table if exists reference_range add column if not exists context_kind varchar(20) not null default 'NONE';
alter table if exists reference_range add column if not exists context_label varchar(255);
alter table if exists reference_range add column if not exists context_min integer;
alter table if exists reference_range add column if not exists context_max integer;

-- Nombre de la persona en app_user: la entidad User lo mapea (para mostrarlo en la app
-- y en los documentos en vez del correo), pero ddl-auto=update no agrega la columna en
-- bases existentes; sin ella, TODA consulta a app_user (login incluido) fallaría. Se
-- agrega idempotente y nullable; los usuarios previos quedan sin nombre y caen al correo.
alter table if exists app_user add column if not exists name varchar(255);

-- Método del examen (lab_tests.method) y diseño del sobre (laboratory.envelope_layout):
-- columnas nuevas y nullable que declaran las entidades LabTest y Laboratory. Igual que
-- las demás de este archivo, ddl-auto=update no siempre las agrega sobre bases ya
-- existentes, y con ellas mapeadas TODA consulta a esas tablas fallaría. Se agregan
-- idempotentes; las filas previas quedan en null (el reporte no imprime método si está
-- vacío y el sobre cae a la distribución 'classic').
alter table if exists lab_tests add column if not exists method varchar(255);
alter table if exists laboratory add column if not exists envelope_layout varchar(255);

-- Restablecimiento de contraseña (app_user.reset_token_hash / reset_expires_at): la
-- entidad User mapea estas dos columnas (token SHA-256 con caducidad), pero el commit
-- que las introdujo NO las registró aquí ni las corrió en prod. Mismo caso que la
-- columna 'name' de arriba: ddl-auto=update no agrega columnas en bases existentes, y
-- con ellas mapeadas TODA consulta a app_user (LOGIN incluido) revienta con "no existe
-- la columna reset_token_hash" -> nadie puede iniciar sesión. Se agregan idempotentes y
-- nullable. El tipo timestamp(6) with time zone es el que Hibernate usa para Instant
-- (igual que invitation_expires_at).
alter table if exists app_user add column if not exists reset_token_hash varchar(64);
alter table if exists app_user add column if not exists reset_expires_at timestamp(6) with time zone;

-- Médico solicitante de la orden (lab_orders.referring_physician): columna nueva y
-- nullable que declara la entidad LabOrder. Igual que las demás de este archivo,
-- ddl-auto=update no siempre la agrega sobre bases ya existentes, y con ella mapeada
-- TODA consulta a lab_orders fallaría. Se agrega idempotente; las órdenes previas
-- quedan en null (el reporte no imprime el médico si está vacío).
alter table if exists lab_orders add column if not exists referring_physician varchar(150);

-- Correo del emisor en la factura (invoices.lab_email): el SAR exige el correo del
-- laboratorio en la factura impresa. laboratory.email ya existe y es editable, pero
-- nunca se agregó al snapshot fiscal que la entidad Invoice congela al emitir (a
-- diferencia de lab_phone, lab_rtn, etc.). Columna nueva y nullable; mismo motivo de
-- siempre, ddl-auto=update no la agrega sobre bases ya existentes. Las facturas ya
-- emitidas quedan en null (el reporte no imprime el correo si está vacío).
alter table if exists invoices add column if not exists lab_email varchar(255);

-- Etiquetas de orden (convenios como "IHSS", campañas, empresas): tablas NUEVAS,
-- no columnas, así que van con "create table if not exists". Igual que el resto de
-- este archivo hay que correrlas a mano en prod con la credencial admin: ddl-auto
-- no tiene permisos DDL ahí, y sin estas tablas TODA consulta a lab_orders revienta
-- (la entidad LabOrder mapea la relación) y con ella se cae órdenes, facturas y el
-- enlace público de resultados.
--
-- La unicidad del nombre es POR LABORATORIO y sobre normalized_name (el nombre sin
-- tildes, sin espacios de más y en minúsculas), para que "IHSS", "ihss" e "Ihss"
-- sean la misma etiqueta y no tres. name guarda cómo se escribió y es lo que se
-- muestra.
create table if not exists order_tags (
  id bigserial primary key,
  laboratory_id bigint,
  name varchar(60) not null,
  normalized_name varchar(60) not null,
  color varchar(7)
);
create unique index if not exists uk_order_tag_name_per_lab on order_tags (laboratory_id, normalized_name);

-- Tabla de unión orden <-> etiqueta. La llave primaria compuesta impide que una
-- orden lleve dos veces la misma etiqueta. El borrado de una etiqueta limpia sus
-- filas aquí desde el servicio (la dueña de la relación es LabOrder, así que
-- Hibernate no lo hace solo); el índice por tag_id es el que usa ese borrado y el
-- filtro "órdenes/facturas con la etiqueta X".
create table if not exists lab_order_tags (
  order_id bigint not null references lab_orders(id),
  tag_id bigint not null references order_tags(id),
  primary key (order_id, tag_id)
);
create index if not exists ix_lab_order_tags_tag on lab_order_tags (tag_id);

-- Interruptor de las alertas del reporte (laboratory.show_report_range_flags):
-- columna nueva que declara la entidad Laboratory para que cada laboratorio decida
-- si su reporte marca los valores fuera de rango — (Alto), (Bajo), (¡Crítico!) y el
-- resaltado en negrita. Sin esta columna en prod TODA consulta a laboratory revienta
-- ("column does not exist") y con ella se caen la configuración, las facturas, las
-- órdenes y el enlace público de resultados.
--
-- Nace en true: el interruptor es opt-out y los laboratorios que ya existían deben
-- seguir imprimiendo las alertas igual que siempre. El default cubre las filas
-- nuevas y el update las que ya estaban; el código igual trata nulo como true, así
-- que este backfill es por orden, no un requisito para que funcione.
alter table if exists laboratory add column if not exists show_report_range_flags boolean default true;
update laboratory set show_report_range_flags = true where show_report_range_flags is null;

-- Adjuntar foto del reporte por examen (test_config.allow_result_attachments): la
-- columna la declara la entidad TestConfig desde el commit 5243610, pero ese commit
-- NO tocó este archivo y en prod ddl-auto ya no tiene permisos DDL. Sin la columna
-- TODA consulta a test_config revienta ("column does not exist"): el editor de
-- exámenes (GET/PUT /api/v1/tests/{id}/full) devuelve 500 y con él se cae el catálogo.
--
-- Nace apagada: el interruptor es opt-in y los exámenes que ya existían no ofrecían
-- adjuntos. El default cubre las filas nuevas y el update las que ya estaban; la
-- entidad la mapea como boolean primitivo, así que un nulo tampoco es aceptable.
alter table if exists test_config add column if not exists allow_result_attachments boolean default false;
update test_config set allow_result_attachments = false where allow_result_attachments is null;

-- Fotos/escaneos del reporte del equipo (test_run_attachments): tabla NUEVA del mismo
-- commit 5243610, que tampoco se registró aquí. La mapea TestRunAttachment y la
-- relación @OneToMany de TestRun, así que sin ella falla subir/leer los adjuntos de
-- una corrida. No lleva laboratory_id: el aislamiento lo hereda de la corrida dueña.
-- object_key es la llave dentro del bucket privado de R2, no una URL.
--
-- OJO con el dueño: en prod esta tabla ya la creó ddl-auto con el rol de la app,
-- que quedó como su dueño, y en Postgres el CREATE INDEX de abajo exige serlo. Con
-- la credencial admin falla con "must be owner of table test_run_attachments";
-- hay que correrlo con el rol de la app (set role / esa conexión) o apropiarse antes
-- de la tabla (alter table test_run_attachments owner to current_user). El índice es
-- solo de rendimiento: sin él nada se rompe.
create table if not exists test_run_attachments (
  id bigserial primary key,
  test_run_id bigint not null references test_runs(id),
  object_key varchar(255) not null,
  content_type varchar(255),
  display_order integer
);
create index if not exists ix_test_run_attachments_run on test_run_attachments (test_run_id);

-- Marcador de novedades vistas (app_user.last_seen_release_version): la entidad User
-- mapea esta columna para recordar, por usuario, la última versión de novedades que
-- ya se le anunció. Igual que 'name' y las de reset de arriba, ddl-auto=update no
-- agrega columnas en bases existentes, y con ella mapeada TODA consulta a app_user
-- (LOGIN incluido) reventaría con "no existe la columna last_seen_release_version".
-- Se agrega idempotente y nullable; no lleva backfill a propósito: null significa "no
-- ha visto nada", que es exactamente lo correcto para quien nunca vio un anuncio.
alter table if exists app_user add column if not exists last_seen_release_version varchar(255);

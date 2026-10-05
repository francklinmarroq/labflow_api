-- schema.sql — el registro de los cambios de esquema de LabFlow.
--
-- Spring NO corre este archivo (spring.sql.init.mode=never). Se corre a mano contra
-- la base, y toda sentencia acá es idempotente: correr el archivo entero dos veces
-- no debe fallar ni cambiar nada la segunda vez.
--
-- ESTADO ACTUAL DE LOS PERMISOS (13-09-2026): el rol con el que el Worker de
-- producción se conecta a PlanetScale tiene el rol `postgres`, así que ddl-auto=update
-- SÍ crea tablas nuevas y agrega columnas nullable en producción por su cuenta, al
-- arrancar. Buena parte de los comentarios de más abajo se escribió durante el período
-- en que ese permiso no estaba, y varios dicen "en prod ddl-auto no tiene permisos
-- DDL": eso es historia del incidente que narran, no el estado de hoy. Para comprobarlo
-- en vez de creerle a un comentario:
--
--   select has_schema_privilege(current_user, 'public', 'CREATE');
--
-- POR QUÉ ESTE ARCHIVO SIGUE EXISTIENDO IGUAL. ddl-auto=update solo agrega. Nunca
-- elimina ni renombra una columna, nunca achica un tipo, nunca rellena datos y nunca
-- actualiza el check constraint de las columnas @Enumerated(STRING). Todo eso solo
-- pasa desde acá. Y aparte de lo que puede o no puede hacer: esto es lo que se revisa
-- en un diff y lo que permite levantar una base desde el repo.
--
-- Reglas que no cambian: sentencias sueltas, sin bloques DO $$ (Spring parte el archivo
-- por cada ";" y no entiende el dollar-quoting), y todo con IF EXISTS / IF NOT EXISTS.

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
-- no columnas, así que van con "create table if not exists". Cuando esto se escribió
-- había que correrlas a mano en prod con la credencial admin porque ddl-auto no tenía
-- permisos DDL ahí (ver la nota del encabezado: hoy sí los tiene). Sin estas tablas
-- TODA consulta a lab_orders revienta
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
-- NO tocó este archivo y en aquel momento ddl-auto ya no tenía permisos DDL en prod
-- (ver la nota del encabezado: hoy sí los tiene). Sin la columna
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

-- Columnas del perfil de examen que este archivo nunca registró: chart_type,
-- result_layout y chart_x_axis_label en test_config, y la tabla de unión
-- test_config_parameters completa con display_order y chart_x_value. Las mapean las
-- entidades TestConfig y TestConfigParameter desde junio y julio de 2026, pero esos
-- commits no tocaron este archivo: existen en prod SOLO porque en esa época
-- ddl-auto=update todavía podía alterar el esquema. Hoy ya no puede, que es justo
-- lo que costó el incidente de allow_result_attachments de arriba.
--
-- CONTRA PRODUCCIÓN NO HAY NADA QUE CORRER: allá ya están las cinco columnas y la
-- tabla, así que todas estas sentencias son no-ops. Se agregan para que una base
-- reconstruida desde este archivo no arranque con el catálogo de exámenes roto en
-- silencio: sin gráfico configurado, sin distribución de antibiograma y sin orden
-- de parámetros en el reporte impreso.
--
-- OJO: esto NO vuelve a este archivo capaz de construir una base desde cero. Las
-- tablas base (tests, parameter, lab_orders, test_runs...) las sigue creando
-- ddl-auto y aquí no están; el bloque de abajo asume que ya existen, igual que los
-- anteriores.
--
-- Ni not null, ni default, ni check constraint, a propósito:
--   * not null — la columna puede ya existir como nullable y ponerle not null exige
--     una segunda sentencia que falla cuando ya lo es, y este archivo no puede
--     ramificar (Spring lo parte por cada ";" y no entiende $$).
--   * default — chartType y resultLayout traen su valor inicial en Java
--     (ChartType.NONE, ResultLayout.STANDARD) y TestConfigServiceImp.toDTO ya lee un
--     nulo como el default, así que la columna nula está cubierta en todos lados.
--   * check — los dos son @Enumerated(STRING) y este archivo YA elimina esos checks
--     más arriba (ver app_role_permission, accounts, tests.area): ddl-auto no los
--     actualiza cuando el enum gana un valor y guardar revienta. La validez la
--     garantiza el enum de Java.
create table if not exists test_config_parameters (
  parameter_id bigint not null references parameter(id),
  test_config_id bigint not null references test_config(id),
  chart_x_value numeric(38,2),
  display_order integer,
  primary key (parameter_id, test_config_id)
);
alter table if exists test_config add column if not exists chart_type varchar(255);
alter table if exists test_config add column if not exists result_layout varchar(255);
alter table if exists test_config add column if not exists chart_x_axis_label varchar(255);
-- Las dos columnas de la tabla de unión van también como alter, no solo dentro del
-- create de arriba: en una base donde test_config_parameters ya existe (prod, y
-- cualquiera que venga de antes de que existieran el orden y la curva) el "create
-- table if not exists" es un no-op y no agregaría nada.
alter table if exists test_config_parameters add column if not exists display_order integer;
alter table if exists test_config_parameters add column if not exists chart_x_value numeric(38,2);

-- Clientes de facturación (billing_clients): el catálogo de empresas, aseguradoras y
-- titulares de convenio a cuyo nombre se puede emitir una factura, separado del padrón
-- de pacientes. Lo mapea la entidad BillingClient y lo referencia invoices.billing_client_id.
--
-- La restricción única va DENTRO del create y no como un alter aparte: Postgres no tiene
-- "add constraint if not exists", así que un alter suelto reventaría en la segunda corrida
-- de este archivo. El precio de esa decisión: si la tabla ya existiera de antes sin la
-- restricción (ddl-auto=update crea tablas nuevas, pero nunca agrega una restricción a una
-- que ya existe), este create es un no-op y la unicidad del RTN quedaría descansando solo
-- en la comprobación del servicio. Por eso vale la pena confirmar en develop que
-- uk_billing_client_rtn_per_lab está realmente en la tabla antes de promover.
create table if not exists billing_clients (
  id bigserial primary key,
  laboratory_id bigint,
  name varchar(255) not null,
  rtn varchar(255) not null,
  phone varchar(255),
  email varchar(255),
  address varchar(255),
  constraint uk_billing_client_rtn_per_lab unique (laboratory_id, rtn)
);

-- A quién se le facturó y de quién son los exámenes. billing_client_id nulo significa
-- "a nombre del paciente", que es lo que dice toda factura anterior a este cambio; en
-- esas, customer_name YA es el paciente y patient_name nulo se lee como ese mismo
-- nombre. Por eso ninguna de las dos lleva backfill ni not null.
alter table if exists invoices add column if not exists billing_client_id bigint references billing_clients(id);
alter table if exists invoices add column if not exists patient_name varchar(255);

-- Médicos solicitantes (referring_physicians): el catálogo de quienes refieren
-- trabajo al laboratorio. Reemplaza la columna de texto libre lab_orders.referring_physician,
-- donde el mismo médico se escribía distinto en cada orden y se imprimía así en el
-- reporte. Lo mapea la entidad ReferringPhysician y lo referencia
-- lab_orders.referring_physician_id.
--
-- La unicidad del nombre es POR LABORATORIO y sobre normalized_name (el nombre sin
-- tildes, sin espacios de más y en minúsculas), para que "Dra. Ana Fúnez",
-- "dra. ana funez" e " Dra.  Ana Funez " sean el mismo médico y no tres. name guarda
-- cómo se escribió y es lo que se imprime.
--
-- Va como "create unique index if not exists" aparte y no como constraint dentro del
-- create (que es lo que hizo billing_clients): Postgres no tiene
-- "add constraint if not exists", pero el índice único sí es idempotente, así que
-- esta forma además arregla una tabla que ya existiera sin la restricción.
create table if not exists referring_physicians (
  id bigserial primary key,
  laboratory_id bigint,
  name varchar(150) not null,
  normalized_name varchar(150) not null
);
create unique index if not exists uk_referring_physician_name_per_lab on referring_physicians (laboratory_id, normalized_name);

-- El médico de la orden deja de ser texto y pasa a apuntar al catálogo. Nullable:
-- la mayoría de las órdenes no indican médico. El índice por referring_physician_id
-- es el que usan el conteo de uso del catálogo y el desenganche al borrar un médico.
alter table if exists lab_orders add column if not exists referring_physician_id bigint references referring_physicians(id);
create index if not exists ix_lab_orders_referring_physician on lab_orders (referring_physician_id);

-- ÚLTIMO PASO DE LA MIGRACIÓN, A MANO Y APARTE — no lo descomente todavía.
-- La columna vieja de texto (lab_orders.referring_physician) ya no la mapea ninguna
-- entidad, así que dejarla no cuesta nada y es la ÚNICA copia de los nombres si el
-- backfill (ReferringPhysicianBackfill) agrupara mal algo. Se corre a mano en cada
-- base SOLO después de haber verificado ahí el resultado del backfill: revisar el
-- renglón del log con cuántas órdenes se reengancharon y cotejar el catálogo contra
-- los nombres que tenía la columna. Antes de eso, volver a la imagen anterior es un
-- rollback completo; después, ya no.
-- alter table lab_orders drop column if exists referring_physician;

-- Métodos del perfil de un examen (test_methods): las técnicas con las que el
-- laboratorio corre ese examen (ELISA, quimioluminiscencia, aglutinación).
-- Reemplazan la columna de texto libre lab_tests.method, donde la misma técnica se
-- reescribía en cada orden y se imprimía en el reporte tal como se hubiera tecleado
-- esa vez. Los mapea la entidad TestMethod y los referencia lab_tests.method_id.
--
-- La unicidad del nombre es POR PERFIL (no por laboratorio) y sobre normalized_name
-- (el nombre sin tildes, sin espacios de más y en minúsculas), para que
-- "Quimioluminiscencia", "quimioluminiscencia" y " Quimioluminiscencia " sean el
-- mismo método y no tres. El mismo nombre bajo dos perfiles son DOS métodos, de dos
-- exámenes distintos. name guarda cómo se escribió y es lo que se imprime.
--
-- is_default marca cuál de los métodos del perfil se le estampa a un examen nuevo al
-- asignarle el perfil. "A lo sumo uno verdadero por perfil" NO se puede expresar como
-- restricción acá (un índice único parcial no cubre el caso de ninguno); lo sostiene
-- TestMethodService, que es el único que lo escribe.
--
-- Va como "create unique index if not exists" aparte y no como constraint dentro del
-- create: Postgres no tiene "add constraint if not exists", pero el índice único sí
-- es idempotente, así que esta forma además arregla una tabla que ya existiera sin la
-- restricción.
create table if not exists test_methods (
  id bigserial primary key,
  laboratory_id bigint,
  test_config_id bigint not null references test_config(id),
  name varchar(255) not null,
  normalized_name varchar(255) not null,
  is_default boolean not null default false
);
create unique index if not exists uk_test_method_name_per_config on test_methods (test_config_id, normalized_name);
-- El índice por perfil es el que usa la lectura de "los métodos de este perfil", que
-- corre en cada apertura de la pantalla de órdenes y del editor del examen.
create index if not exists ix_test_methods_config on test_methods (test_config_id);

-- El método del examen de una orden deja de ser texto y pasa a apuntar al método del
-- perfil. Nullable: un examen puede no indicar método (y los que aún no tienen perfil
-- asignado nunca lo indican). El índice por method_id es el que usa la comprobación de
-- "¿algún examen usa este método?" que bloquea quitarlo del perfil.
alter table if exists lab_tests add column if not exists method_id bigint references test_methods(id);
create index if not exists ix_lab_tests_method on lab_tests (method_id);

-- ÚLTIMO PASO DE LA MIGRACIÓN, A MANO Y APARTE — no lo descomente todavía.
-- La columna vieja de texto (lab_tests.method) ya no la mapea ninguna entidad, así que
-- dejarla no cuesta nada y es la ÚNICA copia de los métodos si el backfill
-- (TestMethodBackfill) agrupara mal algo. Se corre a mano en cada base SOLO después de
-- que el backfill reporte CERO exámenes sin resolver ahí: los exámenes sin perfil no se
-- adivinan, se quedan con su texto y se cuentan en el log, y son exactamente los que
-- este drop perdería. Antes de esto, volver a la imagen anterior es un rollback
-- completo; después, ya no.
-- alter table lab_tests drop column if exists method;

-- Períodos contables (accounting_periods): cada cierre de período del laboratorio,
-- con la partida que trasladó ingresos y gastos a "Resultado del ejercicio" y, si se
-- reabrió, el contra-asiento que la revirtió. Los mapea la entidad AccountingPeriod.
-- Sin unicidad por rango: un período reabierto y vuelto a cerrar deja dos filas con
-- el mismo rango (REOPENED y CLOSED), y las dos son historia. Que no haya dos CLOSED
-- traslapados lo valida AccountingPeriodService al cerrar.
-- Los valores nuevos de journal_entries.source_type (CIERRE, ANULACION_CIERRE) y de
-- accounts.system_key (RESULTADO_DEL_EJERCICIO) no necesitan nada aquí: los check
-- constraints de esas dos columnas ya se eliminaron más arriba. La cuenta
-- "Resultado del ejercicio" la siembra la app en cada laboratorio al primer cierre.
--
-- Orden de despliegue: (1) correr esto en la base, (2) desplegar la imagen del API,
-- (3) mergear el frontend.
create table if not exists accounting_periods (
  id bigserial primary key,
  laboratory_id bigint,
  start_date date not null,
  end_date date not null,
  status varchar(20) not null,
  closing_entry_id bigint references journal_entries(id),
  reversal_entry_id bigint references journal_entries(id),
  result_amount numeric(12,2) not null,
  closed_at timestamp(6) with time zone,
  closed_by_username varchar(255),
  reopened_at timestamp(6) with time zone,
  reopened_by_username varchar(255)
);
create index if not exists ix_accounting_periods_range on accounting_periods (laboratory_id, start_date, end_date);

-- Compras (cambio registrar-compras): proveedores, documentos de compra con sus líneas,
-- pagos a proveedores y el contador de sus recibos. Las mapean Supplier, Purchase,
-- PurchaseLine, SupplierPayment y SupplierPaymentCounter.
--
-- El RTN del proveedor es opcional pero único por laboratorio cuando se indica. La
-- unicidad la sostiene SupplierService (que puede nombrar en el mensaje a quién
-- pertenece); acá solo va el índice que usa esa búsqueda.
--
-- Los valores nuevos de journal_entries.source_type (COMPRA, PAGO_PROVEEDOR y sus
-- anulaciones) y de accounts.system_key (ISV_NO_RECUPERABLE_COMPRAS, cuenta 5107) no
-- necesitan nada aquí: los check constraints de esas columnas ya se eliminaron más
-- arriba, y la cuenta la siembra la app en cada laboratorio la primera vez que se usa.
--
-- Orden de despliegue: (1) correr esto en la base, (2) desplegar la imagen del API,
-- (3) mergear el frontend.
create table if not exists suppliers (
  id bigserial primary key,
  laboratory_id bigint,
  name varchar(255) not null,
  rtn varchar(255),
  phone varchar(255),
  email varchar(255),
  address varchar(255),
  active boolean not null default true
);
create index if not exists ix_suppliers_rtn on suppliers (laboratory_id, rtn);

create table if not exists purchases (
  id bigserial primary key,
  laboratory_id bigint,
  supplier_id bigint not null references suppliers(id),
  purchase_date date not null,
  fiscal_number varchar(255) not null,
  cai varchar(255),
  cai_deadline date,
  purchase_condition varchar(255) not null,
  method varchar(255),
  notes varchar(500),
  exempt_base numeric(12,2) not null,
  taxed_base15 numeric(12,2) not null,
  taxed_base18 numeric(12,2) not null,
  isv15 numeric(12,2) not null,
  isv18 numeric(12,2) not null,
  total numeric(12,2) not null,
  paid_amount numeric(12,2) not null,
  status varchar(20) not null,
  created_at timestamp(6) with time zone,
  created_by_username varchar(255),
  annulled boolean not null default false,
  annulled_at timestamp(6) with time zone,
  annulled_by_username varchar(255),
  annulment_reason varchar(255)
);
create index if not exists ix_purchases_date on purchases (laboratory_id, purchase_date);
create index if not exists ix_purchases_supplier on purchases (supplier_id);

create table if not exists purchase_lines (
  id bigserial primary key,
  laboratory_id bigint,
  purchase_id bigint not null references purchases(id),
  description varchar(500) not null,
  quantity numeric(12,3) not null,
  unit_price numeric(12,2) not null,
  isv_rate varchar(20) not null,
  account_id bigint not null references accounts(id),
  base numeric(12,2) not null,
  isv numeric(12,2) not null,
  line_order integer
);
create index if not exists ix_purchase_lines_purchase on purchase_lines (purchase_id);

create table if not exists supplier_payments (
  id bigserial primary key,
  payment_number bigint,
  laboratory_id bigint,
  purchase_id bigint not null references purchases(id),
  payment_date date not null,
  amount numeric(12,2) not null,
  method varchar(255) not null,
  reference varchar(255),
  created_at timestamp(6) with time zone,
  created_by_username varchar(255),
  annulled boolean not null default false,
  annulled_at timestamp(6) with time zone,
  annulled_by_username varchar(255),
  annulment_reason varchar(255)
);
create unique index if not exists uk_supplier_payment_number_per_lab on supplier_payments (laboratory_id, payment_number);
create index if not exists ix_supplier_payments_purchase on supplier_payments (purchase_id);

create table if not exists supplier_payment_counters (
  laboratory_id bigint primary key,
  next_number bigint not null
);

-- Facturas de varias órdenes y sin orden (cambio facturas-multiorden-y-libres).
--
-- invoice_orders es la relación factura–órdenes y la ÚNICA fuente de "qué factura
-- cubre esta orden": el bloqueo de exámenes, la vista de la orden, la doble
-- facturación y los filtros por orden y etiqueta leen de aquí. No va en las líneas
-- porque una línea agrupada ("10 — Hemograma") junta exámenes de varias órdenes.
-- Congela el paciente de cada orden y su descuento por edad. La mapea InvoiceOrder.
create table if not exists invoice_orders (
  id bigserial primary key,
  laboratory_id bigint,
  invoice_id bigint not null references invoices(id),
  order_id bigint not null references lab_orders(id),
  customer_id bigint references customer(id),
  patient_name varchar(255),
  age_discount_kind varchar(255),
  age_percent numeric(5,2),
  charged_amount numeric(12,2),
  age_discount_amount numeric(12,2)
);
create index if not exists ix_invoice_orders_invoice on invoice_orders (invoice_id);
create index if not exists ix_invoice_orders_order on invoice_orders (order_id);

-- Las líneas ganan cantidad (los exámenes iguales se agrupan) y tipo (examen del
-- catálogo o concepto libre). Las líneas existentes quedan como 1 examen.
alter table if exists invoice_items add column if not exists quantity numeric(12,3) not null default 1;
alter table if exists invoice_items add column if not exists item_type varchar(20) not null default 'EXAMEN';

-- Una factura puede no tener orden (desde cero), ni paciente (a empresa o a
-- consumidor final con órdenes de varios pacientes), ni un tramo de edad único
-- (cuando sus órdenes mezclan tramos). order_id ya no se escribe: se conserva para
-- que la imagen anterior siga leyendo las facturas viejas si hubiera que volver.
-- El add column es un no-op en toda base existente (la columna siempre estuvo); está
-- para que el script también corra sobre una base creada desde cero con el modelo
-- nuevo, que ya no la mapea, sin fallar aquí ni en el insert de abajo.
alter table if exists invoices add column if not exists order_id bigint references lab_orders(id);
alter table if exists invoices alter column order_id drop not null;
alter table if exists invoices alter column customer_id drop not null;
alter table if exists invoices alter column discount_kind drop not null;

-- Migración: una fila de invoice_orders por cada factura emitida antes de este
-- cambio, con su orden, su paciente y su descuento. Idempotente por el "not exists":
-- correrlo dos veces no duplica nada. DEBE correr antes de desplegar la imagen
-- nueva: sin estas filas, las órdenes ya facturadas aparecerían sin factura y se
-- podrían facturar otra vez.
insert into invoice_orders (laboratory_id, invoice_id, order_id, customer_id, patient_name,
                            age_discount_kind, age_percent, charged_amount, age_discount_amount)
select i.laboratory_id, i.id, i.order_id, i.customer_id, coalesce(i.patient_name, i.customer_name),
       i.discount_kind, i.discount_percent,
       (select coalesce(sum(ii.price), 0) from invoice_items ii where ii.invoice_id = i.id),
       i.discount_amount
from invoices i
where i.order_id is not null
  and not exists (select 1 from invoice_orders io where io.invoice_id = i.id);

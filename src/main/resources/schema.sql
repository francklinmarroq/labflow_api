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
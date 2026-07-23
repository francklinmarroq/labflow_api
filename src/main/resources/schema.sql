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
-- La validez ya la garantiza el enum de Java, así que el check sobra: se eliminan
-- todos los check constraints de esa tabla. El bloque es idempotente (si no quedan,
-- no hace nada) y tolera que la tabla aún no exista en instalaciones nuevas.
do $$
declare c record;
begin
  for c in
    select conname from pg_constraint
    where conrelid = 'app_role_permission'::regclass and contype = 'c'
  loop
    execute 'alter table app_role_permission drop constraint ' || quote_ident(c.conname);
  end loop;
exception
  when undefined_table then null;
end $$;
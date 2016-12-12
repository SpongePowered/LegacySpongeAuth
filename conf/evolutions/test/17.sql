# --- !Ups

alter table users add column avatar_url varchar(255) not null default '';
alter table users alter column avatar_url drop default;

alter table users_deleted add column avatar_url varchar(255) not null default '';
alter table users_deleted alter column avatar_url drop default;

# --- !Downs

alter table users_deleted drop column avatar_url;
alter table users drop column avatar_url;

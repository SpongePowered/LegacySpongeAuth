# --- !Ups

alter table users add column is_admin boolean not null default false;

# --- !Downs

alter table users drop column is_admin;

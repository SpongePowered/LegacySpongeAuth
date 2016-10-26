# --- !Ups

alter table users add column salt varchar(255) not null default '';
alter table users alter column salt drop default;

# --- !Downs

alter table users drop column salt;

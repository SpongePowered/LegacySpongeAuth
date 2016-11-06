# --- !Ups

alter table users alter column password drop not null;
alter table users alter column salt drop not null;

alter table users_deleted alter column password drop not null;
alter table users_deleted alter column salt drop not null;

# --- !Downs

alter table users_deleted alter column salt set not null;
alter table users_deleted alter column password set not null;

alter table users alter column salt set not null;
alter table users alter column password set not null;

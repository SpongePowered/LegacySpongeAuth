# --- !Ups

alter table users add column join_date timestamp not null default now();
alter table users alter column join_date drop default;

alter table users_deleted add column join_date timestamp not null default now();
alter table users_deleted alter column join_date drop default;

# --- !Downs

alter table users drop column join_date;
alter table users_deleted drop column join_date;

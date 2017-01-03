# --- !Ups

alter table users_deleted add column google_id varchar(255) unique;

# --- !Downs

alter table users_deleted drop column google_id;

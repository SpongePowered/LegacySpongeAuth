# --- !Ups

alter table users add column google_id varchar(255) unique;

# --- !Downs

alter table users drop column google_id;

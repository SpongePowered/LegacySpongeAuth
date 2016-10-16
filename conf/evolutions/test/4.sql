# --- !Ups

alter table users add column is_email_confirmed boolean default false;

# --- !Downs

alter table users drop column is_email_confirmed;

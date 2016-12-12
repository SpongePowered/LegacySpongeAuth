# --- !Ups

alter table users add column is_totp_confirmed boolean default false;

# --- !Downs

alter table users drop column is_totp_confirmed;

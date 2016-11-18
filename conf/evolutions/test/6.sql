# --- !Ups

alter table users add column totp_secret varchar(255);

# --- !Downs

alter table users drop column totp_secret;

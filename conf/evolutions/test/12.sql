# --- !Ups

alter table users add column failed_totp_attempts int not null default 0;

# --- !Downs

alter table users drop column failed_totp_attempts;

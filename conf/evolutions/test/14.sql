# --- !Ups

alter table users_deleted add column failed_totp_attempts int not null default 0;

# --- !Downs

alter table users_deleted drop column failed_totp_attempts;

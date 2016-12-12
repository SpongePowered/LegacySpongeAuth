# --- !Ups

drop table users_deleted;

create table users_deleted (
  id                  bigserial primary key,
  created_at          timestamp default now(),
  email               varchar(255) not null,
  is_email_confirmed  boolean not null default false,
  username            varchar(20) not null,
  password            varchar(255) not null,
  salt                varchar(255) not null,
  is_admin            boolean not null default false,
  mc_username         varchar(255),
  irc_nick            varchar(255),
  gh_username         varchar(255),
  totp_secret         varchar(255),
  is_totp_confirmed   boolean not null default false,
  failed_totp_attempts int not null default 0,
  deleted_at          timestamp
);

# --- !Downs


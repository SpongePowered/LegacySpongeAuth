# --- !Ups

alter table users add column deleted_at timestamp;

create table users_deleted (
  id                  bigserial primary key,
  created_at          timestamp default now(),
  email               varchar(255) not null unique,
  is_email_confirmed  boolean not null default false,
  username            varchar(20) not null unique,
  password            varchar(255) not null,
  salt                varchar(255) not null,
  is_admin            boolean not null default false,
  mc_username         varchar(255) unique,
  irc_nick            varchar(255),
  gh_username         varchar(255) unique,
  totp_secret         varchar(255),
  is_totp_confirmed   boolean not null default false,
  deleted_at          timestamp
);

# --- !Downs

drop table users_deleted;
alter table users drop column deleted_at;

# --- !Ups

create table users (
  id            bigserial primary key,
  created_at    timestamp default now(),
  email         varchar(255) not null unique,
  username      varchar(20) not null unique,
  password      varchar(255) not null,
  mc_username   varchar(255) unique,
  irc_nick      varchar(255),
  gh_username   varchar(255) unique
);

# --- !Downs

drop table users;

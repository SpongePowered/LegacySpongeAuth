# --- !Ups

create table one_time_passwords(
  id          bigserial not null primary key,
  created_at  timestamp not null,
  user_id     bigint    not null,
  code        int       not null,
  unique (user_id, code)
);

# --- !Downs

drop table one_time_passwords;

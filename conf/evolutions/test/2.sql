# --- !Ups

create table sessions (
  id bigserial PRIMARY KEY,
  created_at timestamp not null,
  expiration timestamp not null,
  username varchar(20) not null,
  token varchar(255) not null unique
);

# --- !Downs

drop table sessions;

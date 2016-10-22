# --- !Ups

create table password_resets (
  id bigserial primary key,
  created_at timestamp not null,
  expiration timestamp not null,
  token varchar(255) not null,
  email varchar(255) not null references users(email) on delete cascade
);

# --- !Downs

drop table password_resets;

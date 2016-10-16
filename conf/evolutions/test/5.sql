# --- !Ups

create table email_confirmations(
  id bigserial primary key,
  created_at timestamp not null,
  expiration timestamp not null,
  email varchar(255) not null unique references users(email) on delete cascade,
  token varchar(255) not null unique
);

# --- !Downs

drop table email_confirmations;

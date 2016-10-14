# --- !Ups

alter table sessions
  add constraint sessions_user_fkey
FOREIGN KEY (username)
REFERENCES users(username)
on delete cascade;

# --- !Downs

alter table sessions drop CONSTRAINT session_user_fkey;

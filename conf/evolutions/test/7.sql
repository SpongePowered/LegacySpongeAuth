# --- !Ups

alter table sessions add column is_authenticated boolean default false;

# --- !Downs

alter table sessions drop column is_authenticated;

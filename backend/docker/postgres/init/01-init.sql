CREATE SCHEMA IF NOT EXISTS develop AUTHORIZATION develop;

GRANT ALL ON SCHEMA develop TO develop;
ALTER ROLE develop SET search_path TO develop, public;

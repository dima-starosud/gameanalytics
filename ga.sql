-- Database: gameanalytics

-- DROP DATABASE gameanalytics;

CREATE DATABASE gameanalytics
  WITH OWNER = postgres
       ENCODING = 'UTF8'
       TABLESPACE = pg_default
       LC_COLLATE = 'en_US.UTF-8'
       LC_CTYPE = 'en_US.UTF-8'
       CONNECTION LIMIT = -1;
GRANT CONNECT, TEMPORARY ON DATABASE gameanalytics TO public;
GRANT ALL ON DATABASE gameanalytics TO postgres;
GRANT ALL ON DATABASE gameanalytics TO ga;

-- Role: ga

-- DROP ROLE ga;

CREATE ROLE ga LOGIN
  NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE NOREPLICATION;

-- Table: events

-- DROP TABLE events;

CREATE TABLE events
(
  user_id bigint NOT NULL,
  time_stamp bigint NOT NULL,
  CONSTRAINT events_primary_key PRIMARY KEY (user_id, time_stamp)
)
WITH (
  OIDS=FALSE
);
ALTER TABLE events
  OWNER TO ga;

-- Index: time_stamp_index

-- DROP INDEX time_stamp_index;

CREATE INDEX time_stamp_index
  ON events
  USING btree
  (time_stamp);

-- Index: user_id_index

-- DROP INDEX user_id_index;

CREATE INDEX user_id_index
  ON events
  USING btree
  (user_id);

-- Rule: events_on_duplicate_ignore ON events

-- DROP RULE events_on_duplicate_ignore ON events;

CREATE OR REPLACE RULE events_on_duplicate_ignore AS
    ON INSERT TO events
   WHERE (EXISTS ( SELECT NULL::unknown
           FROM events
          WHERE events.user_id = new.user_id AND events.time_stamp = new.time_stamp)) DO INSTEAD NOTHING;

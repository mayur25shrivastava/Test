-- One database per service. The outbox table is created up front so Debezium's
-- publication includes it from the very first start.
CREATE DATABASE booking;
CREATE DATABASE flight;
CREATE DATABASE hotel;
CREATE DATABASE cab;

\c booking
CREATE TABLE IF NOT EXISTS outbox_events (
    id           varchar(255) PRIMARY KEY,
    topic        varchar(255),
    message_key  varchar(255),
    type         varchar(255),
    traceparent  varchar(255),
    payload      text,
    created_at   timestamp(6) with time zone,
    published_at timestamp(6) with time zone
);

\c flight
CREATE TABLE IF NOT EXISTS outbox_events (
    id           varchar(255) PRIMARY KEY,
    topic        varchar(255),
    message_key  varchar(255),
    type         varchar(255),
    traceparent  varchar(255),
    payload      text,
    created_at   timestamp(6) with time zone,
    published_at timestamp(6) with time zone
);

\c hotel
CREATE TABLE IF NOT EXISTS outbox_events (
    id           varchar(255) PRIMARY KEY,
    topic        varchar(255),
    message_key  varchar(255),
    type         varchar(255),
    traceparent  varchar(255),
    payload      text,
    created_at   timestamp(6) with time zone,
    published_at timestamp(6) with time zone
);

\c cab
CREATE TABLE IF NOT EXISTS outbox_events (
    id           varchar(255) PRIMARY KEY,
    topic        varchar(255),
    message_key  varchar(255),
    type         varchar(255),
    traceparent  varchar(255),
    payload      text,
    created_at   timestamp(6) with time zone,
    published_at timestamp(6) with time zone
);

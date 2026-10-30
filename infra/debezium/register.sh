#!/bin/sh
# Registers one Debezium connector per service database. Idempotent (PUT .../config).
until curl -sf http://connect:8083/connectors >/dev/null; do echo "waiting for kafka connect..."; sleep 3; done

CONF='{
  "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
  "plugin.name": "pgoutput",
  "database.hostname": "postgres",
  "database.port": "5432",
  "database.user": "postgres",
  "database.password": "postgres",
  "database.dbname": "__DB__",
  "topic.prefix": "cdc-__DB__",
  "slot.name": "outbox_slot___DB__",
  "publication.name": "outbox_pub___DB__",
  "publication.autocreate.mode": "filtered",
  "table.include.list": "public.outbox_events",
  "tombstones.on.delete": "false",
  "key.converter": "org.apache.kafka.connect.storage.StringConverter",
  "value.converter": "org.apache.kafka.connect.storage.StringConverter",
  "transforms": "outbox",
  "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
  "transforms.outbox.table.field.event.id": "id",
  "transforms.outbox.table.field.event.key": "message_key",
  "transforms.outbox.table.field.event.type": "type",
  "transforms.outbox.table.field.event.payload": "payload",
  "transforms.outbox.table.fields.additional.placement": "traceparent:header:traceparent",
  "transforms.outbox.route.by.field": "topic",
  "transforms.outbox.route.topic.replacement": "${routedByValue}"
}'

for db in booking flight hotel cab; do
  echo "registering outbox-$db"
  echo "$CONF" | sed "s/__DB__/$db/g" | \
    curl -s -X PUT "http://connect:8083/connectors/outbox-$db/config" -H 'Content-Type: application/json' -d @-
  echo
done

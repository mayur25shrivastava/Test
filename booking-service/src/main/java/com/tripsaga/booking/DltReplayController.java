package com.tripsaga.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripsaga.common.SagaStep;
import com.tripsaga.common.Topics;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ops endpoint: re-publish everything in "<topic>.DLT" back to <topic>, tagged x-replayed=true.
 * Offsets are committed per batch under a stable group, so a message is not replayed twice.
 * NOTE: add authentication before exposing this beyond a demo.
 */
@RestController
@RequestMapping("/admin/dlt")
public class DltReplayController {
    private static final Set<String> REPLAYABLE = Set.of(
            SagaStep.FLIGHT.commandTopic(), SagaStep.HOTEL.commandTopic(),
            SagaStep.CAB.commandTopic(), Topics.REPLIES);

    private final KafkaProperties kafkaProperties;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper mapper;

    public DltReplayController(KafkaProperties kafkaProperties, KafkaTemplate<String, Object> kafka,
                               ObjectMapper mapper) {
        this.kafkaProperties = kafkaProperties;
        this.kafka = kafka;
        this.mapper = mapper;
    }

    @PostMapping("/replay")
    public ResponseEntity<Map<String, Object>> replay(@RequestParam String topic) throws Exception {
        if (!REPLAYABLE.contains(topic)) {
            return ResponseEntity.badRequest().body(Map.of("error", "topic not replayable", "allowed", REPLAYABLE));
        }
        Map<String, Object> cfg = kafkaProperties.buildConsumerProperties(null);
        cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        cfg.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-replayer-" + topic);
        cfg.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        int replayed = 0;
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(cfg)) {
            consumer.subscribe(List.of(topic + ".DLT"));
            int emptyPolls = 0;
            while (emptyPolls < 3) { // first polls may only join the group
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                if (records.isEmpty()) { emptyPolls++; continue; }
                emptyPolls = 0;
                for (ConsumerRecord<String, String> r : records) {
                    ProducerRecord<String, Object> out = new ProducerRecord<>(topic, r.key(), mapper.readTree(r.value()));
                    out.headers().add("x-replayed", "true".getBytes(StandardCharsets.UTF_8));
                    kafka.send(out).get(5, TimeUnit.SECONDS);
                    replayed++;
                }
                consumer.commitSync();
            }
        }
        return ResponseEntity.ok(Map.of("topic", topic, "replayed", replayed));
    }
}

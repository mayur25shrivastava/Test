package com.tripsaga.booking;

import com.tripsaga.common.SagaStep;
import com.tripsaga.common.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {
    // 3 partitions, keyed by bookingId: all messages of one booking stay ordered.
    @Bean NewTopic flightCommands() { return TopicBuilder.name(SagaStep.FLIGHT.commandTopic()).partitions(3).replicas(1).build(); }
    @Bean NewTopic hotelCommands()  { return TopicBuilder.name(SagaStep.HOTEL.commandTopic()).partitions(3).replicas(1).build(); }
    @Bean NewTopic cabCommands()    { return TopicBuilder.name(SagaStep.CAB.commandTopic()).partitions(3).replicas(1).build(); }
    @Bean NewTopic bookingUpdates() { return TopicBuilder.name(Topics.BOOKING_UPDATES).partitions(1).replicas(1).build(); }
    @Bean NewTopic sagaReplies()    { return TopicBuilder.name(Topics.REPLIES).partitions(3).replicas(1).build(); }
}

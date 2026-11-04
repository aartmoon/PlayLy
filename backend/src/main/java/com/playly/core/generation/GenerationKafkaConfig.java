package com.playly.core.generation;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(name = "playly.kafka.enabled", matchIfMissing = true)
class GenerationKafkaConfig {
    @Bean
    NewTopic generationRequestsTopic() {
        return new NewTopic(GenerationKafka.REQUEST, 3, (short) 1);
    }

    @Bean
    NewTopic generationResultsTopic() {
        return new NewTopic(GenerationKafka.RESULT, 3, (short) 1);
    }

    @Bean
    NewTopic generationDeadLettersTopic() {
        return new NewTopic(GenerationKafka.DLT, 3, (short) 1);
    }

    @Bean
    DefaultErrorHandler generationErrors(KafkaTemplate<String, String> kafka) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, error) -> new TopicPartition(GenerationKafka.DLT, -1));
        recoverer.setFailIfSendResultIsError(true);
        var errors = new DefaultErrorHandler(recoverer, new FixedBackOff(1000, FixedBackOff.UNLIMITED_ATTEMPTS));
        errors.addNotRetryableExceptions(GenerationKafka.InvalidEvent.class);
        return errors;
    }
}

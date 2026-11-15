package com.sh.microservice.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What happens when a message can't be processed.
 * <p>
 * Before: Spring Kafka's default was 10 immediate attempts with no delay, then the message was
 * logged and skipped (lost). A message that couldn't be deserialized (a "poison pill") could
 * also block the partition.
 * <p>
 * Now:
 * <ol>
 *   <li>The value deserializer is wrapped in ErrorHandlingDeserializer (application.properties),
 *       so a bad message becomes a DeserializationException for this handler instead of
 *       failing inside poll().</li>
 *   <li>Temporary failures (e.g. the SMTP server is down) are retried with a fixed back-off.</li>
 *   <li>Once retries are used up, or for errors that can never succeed (such as
 *       DeserializationException, which is non-retryable by default), the record is published
 *       to the <b>dead-letter topic</b> "&lt;topic&gt;-dlt", with headers describing the error.
 *       Someone can inspect it and replay it later.</li>
 * </ol>
 * Spring Boot automatically plugs a DefaultErrorHandler bean into the @KafkaListener container.
 */
@Slf4j
@Configuration
public class KafkaConsumerConfig {

    /** Failed "order-placed" messages go to "order-placed-dlt". */
    public static final String DLT_SUFFIX = "-dlt";

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> avroTemplate,
                                                 ProducerFactory<Object, Object> producerFactory,
                                                 @Value("${app.kafka.retry.interval:2s}") Duration retryInterval,
                                                 @Value("${app.kafka.retry.max-retries:3}") long maxRetries) {
        // Two templates, chosen by the type of value being dead-lettered:
        //  - byte[]: a poison pill. We only have the raw bytes, so they are published as-is.
        //  - anything else (an OrderPlacedEvent that failed to process): Avro, like the source topic.
        KafkaTemplate<Object, Object> bytesTemplate = new KafkaTemplate<>(producerFactory,
                Map.of(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class));

        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, bytesTemplate);
        templates.put(Object.class, avroTemplate);

        // Name the DLT explicitly instead of relying on the library default, which changed from
        // "<topic>.DLT" to "<topic>-dlt" in Spring Kafka 3.2. Partition -1 lets the producer
        // choose, so the DLT doesn't need as many partitions as the source topic.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(templates,
                (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, -1));

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(retryInterval.toMillis(), maxRetries));
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Processing failed for record {}-{}@{} (attempt {}): {}",
                        record.topic(), record.partition(), record.offset(), deliveryAttempt, ex.getMessage()));
        return errorHandler;
    }
}

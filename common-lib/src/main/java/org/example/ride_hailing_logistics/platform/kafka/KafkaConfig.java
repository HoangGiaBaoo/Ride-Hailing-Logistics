package org.example.ride_hailing_logistics.platform.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.util.backoff.FixedBackOff;

import java.util.ArrayList;
import java.util.List;

/**
 * Ha tang Kafka dung chung: tao topic, listener ack thu cong, loi doc -> thu lai roi day sang topic .DLT.
 * Loi tam thoi cua DB (mat ket noi) thu lai mai mai: dua event con dung vao DLT chi vi DB chet la mat event.
 */
@Slf4j
@EnableKafka
@Configuration
public class KafkaConfig {

    public static final String EVENT_LISTENER_FACTORY = "eventListenerFactory";

    @Bean
    public KafkaAdmin.NewTopics rhlTopics(KafkaTopics names, @Value("${app.kafka.partitions:3}") int partitions,
                                          @Value("${app.kafka.replicas:1}") int replicas) {
        List<NewTopic> topics = new ArrayList<>();
        for (String producer : KafkaTopics.PRODUCERS) {
            String name = names.events(producer);
            topics.add(TopicBuilder.name(name).partitions(partitions).replicas(replicas).build());
            topics.add(TopicBuilder.name(name + KafkaTopics.DLT_SUFFIX).partitions(partitions).replicas(replicas).build());
        }
        // Vi tri chi can ban moi nhat cua moi tai xe: giu ngan han
        topics.add(TopicBuilder.name(names.locationUpdates()).partitions(partitions).replicas(replicas)
                .config("retention.ms", "3600000").build());
        return new KafkaAdmin.NewTopics(topics.toArray(NewTopic[]::new));
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        // DLT cung so partition voi topic goc nen giu nguyen partition cua ban tin loi
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + KafkaTopics.DLT_SUFFIX, record.partition()));
        var backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(500);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(5_000);
        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.setBackOffFunction((record, ex) -> isTransient(ex) ? new FixedBackOff(2_000, FixedBackOff.UNLIMITED_ATTEMPTS) : null);
        handler.addNotRetryableExceptions(IllegalArgumentException.class, tools.jackson.core.JacksonException.class);
        return handler;
    }

    @Bean(EVENT_LISTENER_FACTORY)
    public ConcurrentKafkaListenerContainerFactory<Object, Object> eventListenerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer, ConsumerFactory<Object, Object> consumerFactory,
            DefaultErrorHandler errorHandler, @Value("${app.kafka.concurrency:3}") int concurrency) {
        var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
        configurer.configure(factory, consumerFactory);
        factory.setConcurrency(concurrency);
        // Chi commit offset sau khi da ghi inbox xong (at-least-once)
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    private static boolean isTransient(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof TransientDataAccessException || t instanceof DataAccessResourceFailureException
                    || t instanceof CannotCreateTransactionException) {
                return true;
            }
        }
        return false;
    }
}

package com.urlshortener.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the platform's topics so they exist with sane partition counts before any
 * producer/consumer touches them, rather than relying on Kafka's auto-create-on-first-use
 * (which defaults to 1 partition and is disabled in most production clusters anyway).
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic urlCreatedTopic(@Value("${app.kafka.topics.url-created}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic urlClickedTopic(@Value("${app.kafka.topics.url-clicked}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic analyticsTopic(@Value("${app.kafka.topics.analytics}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic deadLetterTopic(@Value("${app.kafka.topics.dead-letter}") String name) {
        return TopicBuilder.name(name).partitions(1).replicas(1).build();
    }
}

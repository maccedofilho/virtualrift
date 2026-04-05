package com.virtualrift.orchestrator.config;

import com.virtualrift.common.events.ScanCompletedEvent;
import com.virtualrift.common.events.ScanFailedEvent;
import com.virtualrift.common.events.ScanRequestedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.ConcurrentKafkaListenerContainerFactory;

import java.util.UUID;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Bean
    public ProducerFactory<String, ScanRequestedEvent> scanRequestedProducerFactory() {
        return new DefaultKafkaProducerFactory<>(
                new org.apache.kafka.clients.producer.ProducerConfig<>(
                        java.util.Map.of(
                                "bootstrap.servers", "localhost:9092",
                                "key.serializer", "org.apache.kafka.common.serialization.StringSerializer",
                                "value.serializer", "org.springframework.kafka.support.serializer.JsonSerializer"
                        )
                )
        );
    }

    @Bean
    public ConsumerFactory<String, ScanCompletedEvent> scanCompletedConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
                java.util.Map.of(
                        "bootstrap.servers", "localhost:9092",
                        "group.id", "virtualrift-orchestrator",
                        "key.deserializer", StringDeserializer.class.getName(),
                        "value.deserializer", "org.springframework.kafka.support.serializer.JsonDeserializer"
                ),
                java.util.Map.of(
                        "spring.json.trusted.packages", "*"
                )
        );
    }

    @Bean
    public ConsumerFactory<String, ScanFailedEvent> scanFailedConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
                java.util.Map.of(
                        "bootstrap.servers", "localhost:9092",
                        "group.id", "virtualrift-orchestrator",
                        "key.deserializer", StringDeserializer.class.getName(),
                        "value.deserializer", "org.springframework.kafka.support.serializer.JsonDeserializer"
                ),
                java.util.Map.of(
                        "spring.json.trusted.packages", "*"
                )
        );
    }
}

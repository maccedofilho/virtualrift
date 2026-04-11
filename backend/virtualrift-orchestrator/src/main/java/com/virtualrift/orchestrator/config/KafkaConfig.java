package com.virtualrift.orchestrator.config;

import com.virtualrift.common.events.ScanCompletedEvent;
import com.virtualrift.common.events.ScanFailedEvent;
import com.virtualrift.common.events.ScanRequestedEvent;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, ScanRequestedEvent> scanRequestedProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put("bootstrap.servers", bootstrapServers);
        config.put("key.serializer", StringSerializer.class);
        config.put("value.serializer", JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, ScanRequestedEvent> scanRequestedKafkaTemplate(
            ProducerFactory<String, ScanRequestedEvent> scanRequestedProducerFactory) {
        return new KafkaTemplate<>(scanRequestedProducerFactory);
    }

    @Bean
    public ConsumerFactory<String, ScanCompletedEvent> scanCompletedConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put("bootstrap.servers", bootstrapServers);
        config.put("group.id", "virtualrift-orchestrator");
        config.put("key.deserializer", StringDeserializer.class);
        config.put("value.deserializer", JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, ScanCompletedEvent.class.getPackageName());
        return new DefaultKafkaConsumerFactory<>(
                config,
                new StringDeserializer(),
                new JsonDeserializer<>(ScanCompletedEvent.class)
        );
    }

    @Bean
    public ConsumerFactory<String, ScanFailedEvent> scanFailedConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put("bootstrap.servers", bootstrapServers);
        config.put("group.id", "virtualrift-orchestrator");
        config.put("key.deserializer", StringDeserializer.class);
        config.put("value.deserializer", JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, ScanFailedEvent.class.getPackageName());
        return new DefaultKafkaConsumerFactory<>(
                config,
                new StringDeserializer(),
                new JsonDeserializer<>(ScanFailedEvent.class)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ScanCompletedEvent> scanCompletedKafkaListenerContainerFactory(
            ConsumerFactory<String, ScanCompletedEvent> scanCompletedConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, ScanCompletedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(scanCompletedConsumerFactory);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ScanFailedEvent> scanFailedKafkaListenerContainerFactory(
            ConsumerFactory<String, ScanFailedEvent> scanFailedConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, ScanFailedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(scanFailedConsumerFactory);
        return factory;
    }
}

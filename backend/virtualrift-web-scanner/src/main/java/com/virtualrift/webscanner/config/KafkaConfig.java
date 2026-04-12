package com.virtualrift.webscanner.config;

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
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
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
    public ConsumerFactory<String, ScanRequestedEvent> scanRequestedConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put("bootstrap.servers", bootstrapServers);
        config.put("group.id", "virtualrift-web-scanner");
        config.put("key.deserializer", StringDeserializer.class);
        config.put("value.deserializer", JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, ScanRequestedEvent.class.getPackageName());
        return new DefaultKafkaConsumerFactory<>(
                config,
                new StringDeserializer(),
                new JsonDeserializer<>(ScanRequestedEvent.class)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ScanRequestedEvent> scanRequestedKafkaListenerContainerFactory(
            ConsumerFactory<String, ScanRequestedEvent> scanRequestedConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, ScanRequestedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(scanRequestedConsumerFactory);
        return factory;
    }

    @Bean
    public ProducerFactory<String, ScanCompletedEvent> scanCompletedProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfig());
    }

    @Bean
    public KafkaTemplate<String, ScanCompletedEvent> scanCompletedKafkaTemplate(
            ProducerFactory<String, ScanCompletedEvent> scanCompletedProducerFactory) {
        return new KafkaTemplate<>(scanCompletedProducerFactory);
    }

    @Bean
    public ProducerFactory<String, ScanFailedEvent> scanFailedProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfig());
    }

    @Bean
    public KafkaTemplate<String, ScanFailedEvent> scanFailedKafkaTemplate(
            ProducerFactory<String, ScanFailedEvent> scanFailedProducerFactory) {
        return new KafkaTemplate<>(scanFailedProducerFactory);
    }

    private Map<String, Object> producerConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("bootstrap.servers", bootstrapServers);
        config.put("key.serializer", StringSerializer.class);
        config.put("value.serializer", JsonSerializer.class);
        return config;
    }
}

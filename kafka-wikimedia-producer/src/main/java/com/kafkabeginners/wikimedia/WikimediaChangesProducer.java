package com.kafkabeginners.wikimedia;

import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.StreamException;
import com.launchdarkly.eventsource.background.BackgroundEventHandler;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;

import java.net.URI;
import java.util.Properties;

public class WikimediaChangesProducer {
    public static void main(String[] args) throws StreamException {

        String bootstrapServers = "localhost:9094";
        String topic = "wikimedia.recentchange";

        final Logger logger = org.slf4j.LoggerFactory.getLogger(WikimediaChangesProducer.class.getSimpleName());


        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", bootstrapServers);
        properties.setProperty("key.serializer", StringSerializer.class.getName());
        properties.setProperty("value.serializer",StringSerializer.class.getName());
        properties.setProperty("compression.type", "snappy");
        properties.setProperty("linger.ms", "20");
        properties.setProperty("batch.size", Integer.toString(32*1024));

        KafkaProducer<String, String> producer = new KafkaProducer<>(properties);

        String url = "https://stream.wikimedia.org/v2/stream/recentchange";
        EventSource eventSource = new EventSource.Builder(URI.create(url)).build();

        eventSource.messages().forEach(e -> {
            logger.info(e.getData());
            producer.send(new ProducerRecord<>(topic, e.getData()));
        });

        eventSource.start();

        producer.flush();

        producer.close();
    }
}

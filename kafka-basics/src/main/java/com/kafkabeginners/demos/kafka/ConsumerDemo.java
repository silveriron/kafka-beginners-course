package com.kafkabeginners.demos.kafka;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class ConsumerDemo {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(ConsumerDemo.class.getSimpleName());

    public static void main(String[] args) {
        String groupId = "my-first-application";

        Properties properties = new Properties();

        properties.setProperty("bootstrap.servers", "127.0.0.1:9094");
        properties.setProperty("key.deserializer", StringDeserializer.class.getName());
        properties.setProperty("value.deserializer",StringDeserializer.class.getName());
        properties.setProperty("group.id", groupId);
        properties.setProperty("auto.offset.reset", "earliest");

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);

        final Thread mainThread = Thread.currentThread();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Caught shutdown hook");

            consumer.wakeup();

            try {
                mainThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));

        try {

            consumer.subscribe(List.of("java_demo"));

            while (true) {

                logger.info("Polling");

                consumer.poll(Duration.ofMillis(1000)).forEach(record -> {
                    logger.info("Key: " + record.key() + ", Value: " + record.value());
                    logger.info("Partition: " + record.partition() + ", Offset: " + record.offset());
                });
            }

        } catch (WakeupException e) {
            logger.info("Received shutdown signal!");
        } catch (Exception e) {
            logger.error("Error", e);
        } finally {
            consumer.close();
            logger.info("Gracefully shutting down...");
        }


    }
}
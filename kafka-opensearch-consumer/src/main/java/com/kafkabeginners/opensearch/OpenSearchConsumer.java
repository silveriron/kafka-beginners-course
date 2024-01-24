package com.kafkabeginners.opensearch;

import com.google.gson.JsonParser;
import org.apache.http.HttpHost;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Properties;

public class OpenSearchConsumer {

    public static RestHighLevelClient createClient() {

        String connectionUrl = "http://localhost:9200";

        RestHighLevelClient restHighLevelClient;

        URI uri = URI.create(connectionUrl);

        String userInfo = uri.getUserInfo();

        if (userInfo == null) {
            restHighLevelClient = new RestHighLevelClient(
                    RestClient.builder(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()))
            );
        } else {
            String[] auth = userInfo.split(":");

            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

            credentialsProvider.setCredentials(
                    org.apache.http.auth.AuthScope.ANY,
                    new org.apache.http.auth.UsernamePasswordCredentials(auth[0], auth[1])
            );

            restHighLevelClient = new RestHighLevelClient(
                    RestClient.builder(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()))
                            .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
                                    .setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy())
                            )
            );
        }

        return restHighLevelClient;
    }

    private static KafkaConsumer<String, String> createKafkaConsumer() {
        String bootstrapServers = "localhost:9094";
        String groupId = "kafka-opensearch-consumer";

        Properties properties = new Properties();

        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        return new KafkaConsumer<>(properties);
    }

    public static void main(String[] args) throws IOException {
        final RestHighLevelClient openSearchClient = createClient();

        final Logger logger = LoggerFactory.getLogger(OpenSearchConsumer.class.getSimpleName());

        KafkaConsumer<String, String> consumer = createKafkaConsumer();

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


        try (openSearchClient; consumer) {

            boolean indexExists = openSearchClient.indices().exists(new GetIndexRequest("wikimedia"), RequestOptions.DEFAULT);

            if (!indexExists) {
                CreateIndexRequest createIndexRequest = new CreateIndexRequest("wikimedia");
                openSearchClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);
                logger.info("Index created successfully");
            } else {
                logger.info("Index already exists");
            }

            consumer.subscribe(java.util.List.of("wikimedia.recentchange"));


            while (true) {
                BulkRequest bulkRequest = new BulkRequest();


                consumer.poll(Duration.ofMillis(3000)).forEach(record -> {

                    String id = extractIdFromRecord(record.value());

                    try {
                        IndexRequest indexRequest = new IndexRequest("wikimedia").id(id)
                                .source(record.value(), XContentType.JSON);

                        bulkRequest.add(indexRequest);

                    } catch (Exception e) {
                        logger.error("Error", e);
                    }
                });

                if (bulkRequest.numberOfActions() > 0) {
                    BulkResponse bulkResponse = openSearchClient.bulk(bulkRequest, RequestOptions.DEFAULT);

                    logger.info("Requests: " + bulkResponse.getItems().length);
                }

                consumer.commitSync();

            }
        } catch (WakeupException e) {
            logger.info("Received shutdown signal!");
        } catch (Exception e) {
            logger.error("Error", e);
        } finally {
            consumer.close();
            openSearchClient.close();
            logger.info("Gracefully shutting down...");
        }
    }

    private static String extractIdFromRecord(String json) {

        return JsonParser.parseString(json)
                .getAsJsonObject()
                .get("meta")
                .getAsJsonObject()
                .get("id")
                .getAsString();
    }
}

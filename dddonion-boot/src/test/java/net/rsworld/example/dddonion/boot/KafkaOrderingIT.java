package net.rsworld.example.dddonion.boot;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"app.outbox.enabled=true"})
class KafkaOrderingIT {

    static final String TOPIC = "orders.events." + UUID.randomUUID();
    static final String AGG_ID = UUID.randomUUID().toString();

    @Container
    static MariaDBContainer maria = new MariaDBContainer("mariadb:11")
            .withUsername("test")
            .withPassword("test")
            .withDatabaseName("test");

    @Container
    static ConfluentKafkaContainer kafka =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.liquibase.url",
                () -> String.format(
                        "jdbc:mariadb://%s:%d/%s",
                        maria.getHost(), maria.getFirstMappedPort(), maria.getDatabaseName()));
        registry.add("spring.liquibase.user", maria::getUsername);
        registry.add("spring.liquibase.password", maria::getPassword);

        registry.add(
                "spring.r2dbc.url",
                () -> String.format(
                        "r2dbc:mariadb://%s:%d/%s",
                        maria.getHost(), maria.getFirstMappedPort(), maria.getDatabaseName()));
        registry.add("spring.r2dbc.username", maria::getUsername);
        registry.add("spring.r2dbc.password", maria::getPassword);

        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("app.topics.orders", () -> TOPIC);
    }

    @Autowired
    DatabaseClient db;

    @BeforeAll
    static void prepareTopic() {
        // optional: AdminClient zum expliziten Anlegen des Topics verwenden
    }

    @Test
    @DisplayName("Publiziert zwei Outbox-Events desselben Aggregats in Kafka strikt nach Sequenz")
    void publishes_two_events_for_same_aggregate_in_sequence() {
        // Zwei Outbox-Events (Seq 1, dann 2) für dieselbe Aggregate-ID einfügen
        db.sql("INSERT INTO outbox_events (id, aggregate_id, sequence, type, occurred_at, payload_json, published) "
                        + "VALUES (:id1, :agg, 1, 'OrderPlaced', :t1, :payload1, false)")
                .bind("id1", UUID.randomUUID().toString())
                .bind("agg", AGG_ID)
                .bind("t1", Instant.now())
                .bind("payload1", "{\"sequence\":1}")
                .fetch()
                .rowsUpdated()
                .block();

        db.sql("INSERT INTO outbox_events (id, aggregate_id, sequence, type, occurred_at, payload_json, published) "
                        + "VALUES (:id2, :agg, 2, 'OrderPlaced', :t2, :payload2, false)")
                .bind("id2", UUID.randomUUID().toString())
                .bind("agg", AGG_ID)
                .bind("t2", Instant.now())
                .bind("payload2", "{\"sequence\":2}")
                .fetch()
                .rowsUpdated()
                .block();

        ReceiverOptions<String, String> options = ReceiverOptions.<String, String>create(Map.of(
                        "bootstrap.servers",
                        kafka.getBootstrapServers(),
                        "group.id",
                        "order-seq-" + UUID.randomUUID(),
                        "auto.offset.reset",
                        "earliest"))
                .subscription(List.of(TOPIC));

        Flux<Integer> sequences = KafkaReceiver.create(options)
                .receive()
                .map(rec -> parseSequence(rec.value()))
                .filter(seq -> seq > 0)
                .take(2); // beendet sich nach 2 Events

        StepVerifier.create(sequences).expectNext(1, 2).verifyComplete(); // optional: .verify(Duration.ofSeconds(30));
    }

    private int parseSequence(String json) {
        try {
            JsonNode n = new ObjectMapper().readTree(json);
            return n.has("sequence") ? n.get("sequence").asInt() : -1;
        } catch (Exception _) {
            return -1;
        }
    }
}

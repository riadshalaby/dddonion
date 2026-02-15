package net.rsworld.example.dddonion.boot;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import io.restassured.RestAssured;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
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
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.outbox.enabled=true"})
class KafkaOutboxIT {

    @Value("${local.server.port}")
    int port;

    static final String TOPIC = "orders.events." + UUID.randomUUID();

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

    @BeforeAll
    static void setup() throws Exception {
        RestAssured.useRelaxedHTTPSValidation();
        // Ensure topic exists (in case auto.create.topics.enable is off)
        Properties props = new Properties();
        props.put("bootstrap.servers", kafka.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(props)) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get();
        } catch (Exception _) {
            // topic may already exist
        }
    }

    @Test
    @DisplayName("Veröffentlicht nach HTTP-Order-Erstellung ein Event mit Sequenz 1 auf Kafka")
    void publishes_event_to_kafka_when_order_created() {
        // Create a Kafka receiver subscribed to the topic
        ReceiverOptions<String, String> options = ReceiverOptions.<String, String>create(Map.of(
                        "bootstrap.servers",
                        kafka.getBootstrapServers(),
                        "group.id",
                        "test-" + UUID.randomUUID(),
                        "auto.offset.reset",
                        "earliest"))
                .subscription(List.of(TOPIC));

        Flux<String> values = KafkaReceiver.create(options).receive().map(rec -> rec.value());

        // Trigger domain event via REST
        String orderId = given().port(port)
                .param("email", "kafka-it@example.com")
                .param("total", new BigDecimal("21.00"))
                .when()
                .post("/orders")
                .then()
                .statusCode(200)
                .body(notNullValue())
                .extract()
                .asString();

        ObjectMapper mapper = new ObjectMapper();

        StepVerifier.create(values.map(json -> parseSequence(mapper, json))
                        .filter(seq -> seq >= 1)
                        .take(1))
                .expectNext(1L)
                .thenCancel()
                .verify(Duration.ofSeconds(30));
    }

    private long parseSequence(ObjectMapper mapper, String json) {
        try {
            JsonNode n = mapper.readTree(json);
            // our OrderPlaced record serializes 'sequence' field
            return n.has("sequence") ? n.get("sequence").asLong() : -1L;
        } catch (Exception _) {
            return -1L;
        }
    }
}

package net.rsworld.example.dddonion.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.rsworld.example.dddonion.bootstrap.DddOnionApplication;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.domain.order.event.OrderPlaced;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

@Testcontainers
@SpringBootTest(classes = DddOnionApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerExposureIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    ApplicationEventPublisher events;

    private WebTestClient client;

    @Container
    static MariaDBContainer maria = new MariaDBContainer("mariadb:11")
            .withUsername("test")
            .withPassword("test")
            .withDatabaseName("test");

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
    }

    @BeforeEach
    void setUp() {
        client =
                WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String registerCustomer(String creditLimit) {
        var body = client.post()
                .uri("/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\": \"it-" + UUID.randomUUID() + "@example.com\", \"creditLimit\": " + creditLimit
                        + "}")
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        assertThat(body).isNotNull();
        return (String) body.get("customerId");
    }

    private int placeOrder(String customerId, String total) {
        return client.post()
                .uri("/orders")
                .header("X-Actor-Type", "BUSINESS_CUSTOMER")
                .header("X-Correlation-Id", "it-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"customerId\": \"" + customerId + "\", \"total\": " + total + "}")
                .exchange()
                .returnResult(String.class)
                .getStatus()
                .value();
    }

    private BigDecimal openAmount(String customerId) {
        var body = client.get()
                .uri("/customers/" + customerId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        assertThat(body).isNotNull();
        return new BigDecimal(String.valueOf(body.get("openAmount")));
    }

    private void awaitOpenAmount(String customerId, String expected) {
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(openAmount(customerId)).isEqualByComparingTo(expected));
    }

    @Test
    void placedOrderIncreasesOpenAmountEventually() {
        String customerId = registerCustomer("100");

        assertThat(placeOrder(customerId, "60")).isEqualTo(201);

        awaitOpenAmount(customerId, "60");
    }

    @Test
    void orderBeyondRemainingCreditIsRejected() {
        String customerId = registerCustomer("100");
        assertThat(placeOrder(customerId, "60")).isEqualTo(201);
        awaitOpenAmount(customerId, "60");

        assertThat(placeOrder(customerId, "50")).isEqualTo(422);
    }

    /** Places {@code count} orders truly in parallel: every thread waits at a start gate before sending. */
    private List<Integer> placeOrdersConcurrently(String customerId, int count, String total) throws Exception {
        var pool = Executors.newFixedThreadPool(count);
        try {
            var startGate = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                results.add(pool.submit(() -> {
                    startGate.await();
                    return placeOrder(customerId, total);
                }));
            }
            startGate.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentExposuresAreNotLost() throws Exception {
        String customerId = registerCustomer("1000");

        assertThat(placeOrdersConcurrently(customerId, 5, "10")).containsOnly(201);

        awaitOpenAmount(customerId, "50");
    }

    @Test
    void burstOfTenExposuresIsNotLost() throws Exception {
        String customerId = registerCustomer("1000");

        // 10 = attempts on the event path: a conflict means another update committed, so 10 updates cannot lose any
        assertThat(placeOrdersConcurrently(customerId, 10, "10")).containsOnly(201);

        awaitOpenAmount(customerId, "100");
    }

    @Test
    void exposureForUnknownCustomerIsAuditedAsError() {
        events.publishEvent(new OrderPlaced(
                OrderId.newId().value(), 1L, Instant.now(), CustomerId.newId().value(), new BigDecimal("10.00")));

        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> client.get()
                        .uri("/actuator/prometheus")
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(String.class)
                        .value(body -> assertThat(body)
                                .containsPattern("usecase_execution_seconds_count\\{[^}]*channel=\"event\""
                                        + "[^}]*outcome=\"ERROR\""
                                        + "[^}]*usecase=\"customer.record-order-exposure\"[^}]*}")));
    }

    @Test
    void exposureIsAudited() {
        String customerId = registerCustomer("100");
        assertThat(placeOrder(customerId, "5")).isEqualTo(201);
        awaitOpenAmount(customerId, "5");

        client.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .containsPattern("usecase_execution_seconds_count\\{[^}]*actor_type=\"BUSINESS_CUSTOMER\""
                                + "[^}]*channel=\"event\"[^}]*outcome=\"SUCCESS\""
                                + "[^}]*usecase=\"customer.record-order-exposure\"[^}]*}"));
    }
}

package net.rsworld.example.dddonion.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.rsworld.example.dddonion.bootstrap.DddOnionApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

@Testcontainers
@SpringBootTest(classes = DddOnionApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerCreditIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    DatabaseClient db;

    private WebTestClient client;

    @Container
    static MariaDBContainer maria = new MariaDBContainer("mariadb:11.8")
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

    private long orderCount(String customerId) {
        Long count = db.sql("SELECT COUNT(*) FROM orders WHERE customer_id = :id")
                .bind("id", customerId)
                .map(row -> row.get(0, Long.class))
                .one()
                .block(Duration.ofSeconds(10));
        assertThat(count).isNotNull();
        return count;
    }

    @Test
    void placedOrderReservesCreditImmediately() {
        String customerId = registerCustomer("100");

        assertThat(placeOrder(customerId, "60")).isEqualTo(201);

        assertThat(openAmount(customerId)).isEqualByComparingTo("60");
    }

    @Test
    void orderBeyondRemainingCreditIsRejected() {
        String customerId = registerCustomer("100");
        assertThat(placeOrder(customerId, "60")).isEqualTo(201);

        assertThat(placeOrder(customerId, "50")).isEqualTo(422);

        assertThat(openAmount(customerId)).isEqualByComparingTo("60");
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
    void concurrentOrdersWithinLimitAreAllAccepted() throws Exception {
        String customerId = registerCustomer("1000");

        assertThat(placeOrdersConcurrently(customerId, 3, "10")).containsOnly(201);

        assertThat(openAmount(customerId)).isEqualByComparingTo("30");
        assertThat(orderCount(customerId)).isEqualTo(3);
    }

    @Test
    void concurrentOrdersNeverExceedTheLimit() throws Exception {
        String customerId = registerCustomer("100");

        List<Integer> statuses = placeOrdersConcurrently(customerId, 5, "30");

        // how many requests win, lose the version race (409) or hit the limit (422) is timing dependent
        assertThat(statuses).isSubsetOf(201, 409, 422);
        long accepted = statuses.stream().filter(status -> status == 201).count();
        assertThat(accepted).isBetween(1L, 3L);
        assertThat(openAmount(customerId)).isEqualByComparingTo(BigDecimal.valueOf(30 * accepted));
        assertThat(orderCount(customerId)).isEqualTo(accepted);
    }
}

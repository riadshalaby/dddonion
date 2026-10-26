package net.rsworld.example.dddonion.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import net.rsworld.example.dddonion.bootstrap.DddOnionApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

@Testcontainers
@SpringBootTest(classes = DddOnionApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebfluxOrderApiIT {

    @Value("${local.server.port}")
    int port;

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

    @Test
    @DisplayName("Erstellt eine Order (201 + Location) und liest sie anschließend per GET wieder aus")
    void createThenGetOrder_roundtrip() {
        String customerId = registerCustomer("1000");
        EntityExchangeResult<byte[]> created = client.post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"customerId\": \"" + customerId + "\", \"total\": 19.99}")
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .exists("Location")
                .expectBody()
                .jsonPath("$.orderId")
                .isNotEmpty()
                .returnResult();

        URI location = created.getResponseHeaders().getLocation();
        assertThat(location).isNotNull();

        client.get()
                .uri(location.toString())
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.customerId")
                .isEqualTo(customerId)
                .jsonPath("$.status")
                .isEqualTo("PLACED");
    }

    @Test
    @DisplayName("Weist ungültige Eingaben mit 400 problem+json zurück")
    void createOrder_shouldRejectInvalidInput() {
        client.post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"customerId\": \"not-a-uuid\", \"total\": 19.99}")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("Weist eine Order für einen unbekannten Customer mit 422 problem+json zurück")
    void createOrder_shouldReturn422ForUnknownCustomer() {
        client.post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"customerId\": \"" + UUID.randomUUID() + "\", \"total\": 1.00}")
                .exchange()
                .expectStatus()
                .isEqualTo(422)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("Lehnt eine Order über dem Kreditlimit mit 422 ab und akzeptiert sie exakt am Limit")
    void createOrder_shouldReturn422WhenCreditLimitExceeded() {
        String customerId = registerCustomer("10");

        client.post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"customerId\": \"" + customerId + "\", \"total\": 10.01}")
                .exchange()
                .expectStatus()
                .isEqualTo(422)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);

        client.post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"customerId\": \"" + customerId + "\", \"total\": 10.00}")
                .exchange()
                .expectStatus()
                .isCreated();
    }

    @Test
    @DisplayName("Liefert 404 problem+json für eine unbekannte Order")
    void getOrder_shouldReturn404ForUnknownId() {
        client.get()
                .uri("/orders/7a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("Misst jede Use-Case-Ausführung mit Use Case und Actor-Typ als Prometheus-Labels")
    void exposesUseCaseMetricsPerActorType() {
        String customerId = registerCustomer("1000");
        client.post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Actor-Type", "BUSINESS_CUSTOMER")
                .bodyValue("{\"customerId\": \"" + customerId + "\", \"total\": 5.00}")
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .exists("X-Correlation-Id");

        client.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .containsPattern("usecase_execution_seconds_count\\{[^}]*actor_type=\"BUSINESS_CUSTOMER\""
                                + "[^}]*usecase=\"order.place\"[^}]*usecase_kind=\"command\"[^}]*}")
                        .doesNotContain("correlation_id"));
    }

    @Test
    @DisplayName("Stellt den OpenAPI-Vertrag unter /openapi.yaml bereit")
    void exposesOpenApiSpec() {
        client.get()
                .uri("/openapi.yaml")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.parseMediaType("application/yaml"))
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("openapi:"));
    }

    @Test
    @DisplayName("Serviert Swagger UI unter /swagger-ui.html samt Webjar-Assets")
    void exposesSwaggerUi() {
        client.get()
                .uri("/swagger-ui.html")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("SwaggerUIBundle"));

        // version-agnostic webjar path resolves via webjars-locator-lite
        client.get()
                .uri("/webjars/swagger-ui/swagger-ui-bundle.js")
                .exchange()
                .expectStatus()
                .isOk();
    }
}

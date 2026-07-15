package net.rsworld.example.dddonion.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
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

    @Test
    @DisplayName("Erstellt eine Order (201 + Location) und liest sie anschließend per GET wieder aus")
    void createThenGetOrder_roundtrip() {
        EntityExchangeResult<byte[]> created = client.post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"customerEmail\": \"test@example.com\", \"total\": 19.99}")
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
                .jsonPath("$.customerEmail")
                .isEqualTo("test@example.com")
                .jsonPath("$.status")
                .isEqualTo("PLACED");
    }

    @Test
    @DisplayName("Weist ungültige Eingaben mit 400 problem+json zurück")
    void createOrder_shouldRejectInvalidInput() {
        client.post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"customerEmail\": \"a@b\", \"total\": 19.99}")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("Liefert 404 problem+json für eine unbekannte Order")
    void getOrder_shouldReturn404ForUnknownId() {
        client.get()
                .uri("/orders/does-not-exist")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
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

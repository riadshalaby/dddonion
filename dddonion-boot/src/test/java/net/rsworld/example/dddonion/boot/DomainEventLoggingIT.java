package net.rsworld.example.dddonion.boot;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.OutputStreamAppender;
import io.restassured.RestAssured;
import java.io.ByteArrayOutputStream;
import net.rsworld.example.dddonion.bootstrap.DddOnionApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

@Testcontainers
@SpringBootTest(classes = DddOnionApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DomainEventLoggingIT {

    @LocalServerPort
    int port;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private OutputStreamAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender;

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

    @BeforeAll
    static void setupRestAssured() {
        RestAssured.useRelaxedHTTPSValidation();
    }

    @AfterEach
    void tearDown() {
        if (appender != null) {
            appender.stop();
            ((Logger) LoggerFactory.getLogger("net.rsworld.example.dddonion.monitor.DomainEventLoggingListener"))
                    .detachAppender(appender);
        }
    }

    @Test
    @DisplayName("Schreibt beim Bestellen per HTTP ein OrderPlaced-DomainEvent ins Monitoring-Log")
    void placingOrder_viaHttp_emitsDomainEvent_which_is_logged() {
        // Capture logs from the listener
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger =
                (Logger) LoggerFactory.getLogger("net.rsworld.example.dddonion.monitor.DomainEventLoggingListener");
        logger.setLevel(Level.INFO);

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(ctx);
        encoder.setPattern("%msg%n");
        encoder.start();

        appender = new OutputStreamAppender<>();
        appender.setContext(ctx);
        appender.setEncoder(encoder);
        appender.setOutputStream(out);
        appender.start();

        logger.addAppender(appender);

        // Call HTTP endpoint to place an order
        given().baseUri("http://localhost")
                .port(port)
                .queryParam("email", "john.doe@example.com")
                .queryParam("total", "42.50")
                .when()
                .post("/orders")
                .then()
                .statusCode(200);

        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            String logged = out.toString();
            assertThat(logged).contains("DomainEvent received");
            assertThat(logged).contains("type=OrderPlaced");
            assertThat(logged).contains("aggregateId=");
            assertThat(logged).contains("sequence=1");
            assertThat(logged).contains("occurredAt=");
        });
    }
}

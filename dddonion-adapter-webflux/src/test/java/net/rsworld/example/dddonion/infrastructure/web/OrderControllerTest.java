package net.rsworld.example.dddonion.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import net.rsworld.example.dddonion.application.order.usecase.PlaceOrderUseCase;
import net.rsworld.example.dddonion.domain.order.command.PlaceOrderCommand;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/** WebFlux slice test for OrderController. Uses WebTestClient bound to the controller with a mocked UseCase. */
class OrderControllerTest {

    private PlaceOrderUseCase placeOrderUseCase;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        placeOrderUseCase = mock(PlaceOrderUseCase.class);
        OrderController controller = new OrderController(placeOrderUseCase);
        this.webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    @DisplayName("POST /orders returns created OrderId as plain text when valid params provided")
    void createReturnsOrderId() {
        // Arrange
        String email = "test@example.com";
        BigDecimal total = new BigDecimal("12.34");
        OrderId returnedId = new OrderId("ORD-123");
        when(placeOrderUseCase.handle(any(PlaceOrderCommand.class))).thenReturn(Mono.just(returnedId));

        // Act + Assert
        String body = webTestClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/orders")
                        .queryParam("email", email)
                        .queryParam("total", total)
                        .build())
                .accept(MediaType.TEXT_PLAIN)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isEqualTo("ORD-123");

        // Verify correct command passed to use case
        ArgumentCaptor<PlaceOrderCommand> cmdCaptor = ArgumentCaptor.forClass(PlaceOrderCommand.class);
        verify(placeOrderUseCase, times(1)).handle(cmdCaptor.capture());
        PlaceOrderCommand usedCmd = cmdCaptor.getValue();
        assertThat(usedCmd.customerEmail()).isEqualTo(email);
        assertThat(usedCmd.total()).isEqualByComparingTo(total);
    }

    @Test
    @DisplayName("POST /orders validates required query params")
    void createValidatesParams() {
        // Missing both params
        webTestClient.post().uri("/orders").exchange().expectStatus().isBadRequest();

        // Present email, missing total
        webTestClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/orders")
                        .queryParam("email", "test@example.com")
                        .build())
                .exchange()
                .expectStatus()
                .isBadRequest();

        // Present total, missing email
        webTestClient
                .post()
                .uri(uriBuilder ->
                        uriBuilder.path("/orders").queryParam("total", "10.00").build())
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(placeOrderUseCase);
    }

    @Test
    @DisplayName("POST /orders maps BigDecimal and returns 400 on invalid numeric value")
    void createRejectsInvalidTotal() {
        webTestClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/orders")
                        .queryParam("email", "test@example.com")
                        .queryParam("total", "NaN")
                        .build())
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(placeOrderUseCase);
    }

    @Test
    @DisplayName("POST /orders propagates errors from use case as 5xx by default")
    void createPropagatesUseCaseError() {
        when(placeOrderUseCase.handle(any())).thenReturn(Mono.error(new IllegalStateException("boom")));

        webTestClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/orders")
                        .queryParam("email", "x@y.ze")
                        .queryParam("total", "1.00")
                        .build())
                .exchange()
                .expectStatus()
                .is5xxServerError();

        verify(placeOrderUseCase, times(1)).handle(any());
    }

    @Nested
    @DisplayName("Content negotiation")
    class ContentNegotiation {

        @Test
        @DisplayName("Returns 406 when client only accepts JSON (endpoint produces text/plain)")
        void returns406WhenJsonRequested() {
            when(placeOrderUseCase.handle(any())).thenReturn(Mono.just(new OrderId("ORD-42")));

            webTestClient
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/orders")
                            .queryParam("email", "a@b.c")
                            .queryParam("total", "2.50")
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus()
                    .isEqualTo(406);
        }

        @Test
        @DisplayName("Returns text/plain when client accepts text/plain")
        void returnsTextPlainWhenAccepted() {
            when(placeOrderUseCase.handle(any())).thenReturn(Mono.just(new OrderId("ORD-42")));

            webTestClient
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/orders")
                            .queryParam("email", "a@b.ce")
                            .queryParam("total", "2.50")
                            .build())
                    .accept(MediaType.TEXT_PLAIN)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectHeader()
                    .contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
                    .expectBody(String.class)
                    .isEqualTo("ORD-42");
        }
    }
}

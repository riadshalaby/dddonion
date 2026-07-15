package net.rsworld.example.dddonion.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import net.rsworld.example.dddonion.application.order.usecase.GetOrderUseCase;
import net.rsworld.example.dddonion.application.order.usecase.PlaceOrderUseCase;
import net.rsworld.example.dddonion.domain.order.command.PlaceOrderCommand;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import net.rsworld.example.dddonion.infrastructure.web.dto.PlaceOrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/** WebFlux slice test for OrderController. Uses WebTestClient bound to the controller with a mocked UseCase. */
class OrderControllerTest {

    private PlaceOrderUseCase placeOrderUseCase;
    private GetOrderUseCase getOrderUseCase;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        placeOrderUseCase = mock(PlaceOrderUseCase.class);
        getOrderUseCase = mock(GetOrderUseCase.class);
        OrderController controller = new OrderController(placeOrderUseCase, getOrderUseCase);
        this.webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /orders returns 201 with created OrderId as JSON when the body is valid")
    void createReturnsOrderId() {
        // Arrange
        String email = "test@example.com";
        BigDecimal total = new BigDecimal("12.34");
        OrderId returnedId = new OrderId("ORD-123");
        when(placeOrderUseCase.handle(any(PlaceOrderCommand.class))).thenReturn(Mono.just(returnedId));

        // Act + Assert
        webTestClient
                .post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PlaceOrderRequest(email, total))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .valueEquals("Location", "/orders/ORD-123")
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.orderId")
                .isEqualTo("ORD-123");

        // Verify correct command passed to use case
        ArgumentCaptor<PlaceOrderCommand> cmdCaptor = ArgumentCaptor.forClass(PlaceOrderCommand.class);
        verify(placeOrderUseCase, times(1)).handle(cmdCaptor.capture());
        PlaceOrderCommand usedCmd = cmdCaptor.getValue();
        assertThat(usedCmd.customerEmail()).isEqualTo(email);
        assertThat(usedCmd.total()).isEqualByComparingTo(total);
    }

    @Test
    @DisplayName("POST /orders returns 400 when the e-mail is missing")
    void createRejectsMissingEmail() {
        webTestClient
                .post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"total\": 10.00}")
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(placeOrderUseCase);
    }

    @Test
    @DisplayName("POST /orders returns 400 on a negative total")
    void createRejectsNegativeTotal() {
        webTestClient
                .post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"customerEmail\": \"test@example.com\", \"total\": -1.00}")
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(placeOrderUseCase);
    }

    @Test
    @DisplayName("POST /orders returns 400 problem+json when the domain rejects the e-mail")
    void createReturnsProblemJsonOnDomainValidationError() {
        // "a@b" passes lenient @Email bean validation but fails the stricter domain regex,
        // so the IllegalArgumentException is raised inside the reactive chain and handled by the advice.
        webTestClient
                .post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"customerEmail\": \"a@b\", \"total\": 10.00}")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(400);

        verifyNoInteractions(placeOrderUseCase);
    }

    @Test
    @DisplayName("POST /orders propagates errors from the use case as 5xx by default")
    void createPropagatesUseCaseError() {
        when(placeOrderUseCase.handle(any())).thenReturn(Mono.error(new IllegalStateException("boom")));

        webTestClient
                .post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PlaceOrderRequest("x@y.ze", new BigDecimal("1.00")))
                .exchange()
                .expectStatus()
                .is5xxServerError();

        verify(placeOrderUseCase, times(1)).handle(any());
    }

    @Test
    @DisplayName("GET /orders/{id} returns 200 with the order view when it exists")
    void getReturnsOrder() {
        Order placed = new Order("buyer@example.com", new BigDecimal("42.00"));
        placed.place(); // NEW -> PLACED
        when(getOrderUseCase.findById(new OrderId("ORD-7"))).thenReturn(Mono.just(placed));

        webTestClient
                .get()
                .uri("/orders/{id}", "ORD-7")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.customerEmail")
                .isEqualTo("buyer@example.com")
                .jsonPath("$.status")
                .isEqualTo("PLACED");
    }

    @Test
    @DisplayName("GET /orders/{id} returns 404 problem+json when the order is unknown")
    void getReturnsNotFound() {
        when(getOrderUseCase.findById(new OrderId("missing"))).thenReturn(Mono.empty());

        webTestClient
                .get()
                .uri("/orders/{id}", "missing")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(404);
    }
}

package net.rsworld.example.dddonion.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import net.rsworld.example.dddonion.application.order.usecase.GetOrderUseCase;
import net.rsworld.example.dddonion.application.order.usecase.PlaceOrderUseCase;
import net.rsworld.example.dddonion.domain.order.command.PlaceOrderCommand;
import net.rsworld.example.dddonion.domain.order.model.CustomerEmail;
import net.rsworld.example.dddonion.domain.order.model.Money;
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

    // OrderId now enforces canonical UUID form, so tests must use real UUIDs.
    private static final String ID = "3f6a9c1e-4b2d-4f0a-9c7e-8a1b2c3d4e5f";
    private static final String ID_2 = "7a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";

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
        OrderId returnedId = new OrderId(ID);
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
                .valueEquals("Location", "/orders/" + ID)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.orderId")
                .isEqualTo(ID);

        // Verify correct command (built from value objects) passed to the use case
        ArgumentCaptor<PlaceOrderCommand> cmdCaptor = ArgumentCaptor.forClass(PlaceOrderCommand.class);
        verify(placeOrderUseCase, times(1)).handle(cmdCaptor.capture());
        PlaceOrderCommand usedCmd = cmdCaptor.getValue();
        assertThat(usedCmd.customerEmail().value()).isEqualTo(email);
        assertThat(usedCmd.total().amount()).isEqualByComparingTo(total);
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
    @DisplayName("POST /orders returns 400 when an e-mail fails the strict pattern at the boundary")
    void createRejectsInvalidEmailAtBoundary() {
        // "a@b" passes the lenient @Email check but fails the strict pattern that the OpenAPI contract
        // mirrors from the CustomerEmail value object, so boundary validation rejects it before any use case runs.
        webTestClient
                .post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"customerEmail\": \"a@b\", \"total\": 10.00}")
                .exchange()
                .expectStatus()
                .isBadRequest();

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
        Order placed = new Order(new CustomerEmail("buyer@example.com"), new Money(new BigDecimal("42.00")));
        placed.place(); // NEW -> PLACED
        when(getOrderUseCase.findById(new OrderId(ID))).thenReturn(Mono.just(placed));

        webTestClient
                .get()
                .uri("/orders/{id}", ID)
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
        when(getOrderUseCase.findById(new OrderId(ID_2))).thenReturn(Mono.empty());

        webTestClient
                .get()
                .uri("/orders/{id}", ID_2)
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("GET /orders/{id} returns 400 when the id is not a canonical UUID")
    void getRejectsMalformedId() {
        webTestClient
                .get()
                .uri("/orders/{id}", "not-a-uuid")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(400);

        verifyNoInteractions(getOrderUseCase);
    }
}

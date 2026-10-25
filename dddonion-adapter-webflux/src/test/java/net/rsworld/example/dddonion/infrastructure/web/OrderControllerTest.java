package net.rsworld.example.dddonion.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import net.rsworld.example.dddonion.application.context.ActorType;
import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.application.order.UnknownCustomerException;
import net.rsworld.example.dddonion.application.order.usecase.GetOrderUseCase;
import net.rsworld.example.dddonion.application.order.usecase.PlaceOrderUseCase;
import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.model.CreditLimitExceededException;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.domain.order.command.PlaceOrderCommand;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import net.rsworld.example.dddonion.infrastructure.web.dto.PlaceOrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;
import reactor.core.publisher.Mono;

/** WebFlux slice test for OrderController. Uses WebTestClient bound to the controller with a mocked UseCase. */
class OrderControllerTest {

    // OrderId now enforces canonical UUID form, so tests must use real UUIDs.
    private static final String ID = "3f6a9c1e-4b2d-4f0a-9c7e-8a1b2c3d4e5f";
    private static final String ID_2 = "7a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";
    private static final String CUSTOMER = "11111111-2222-4333-8444-555555555555";

    private PlaceOrderUseCase placeOrderUseCase;
    private GetOrderUseCase getOrderUseCase;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        placeOrderUseCase = mock(PlaceOrderUseCase.class);
        getOrderUseCase = mock(GetOrderUseCase.class);
        // Mirror the application context: @Validated on the generated interface triggers method validation via a proxy.
        ProxyFactory proxy = new ProxyFactory(new OrderController(placeOrderUseCase, getOrderUseCase));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new MethodValidationInterceptor());
        this.webTestClient = WebTestClient.bindToController(proxy.getProxy())
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /orders returns 201 with created OrderId as JSON when the body is valid")
    void createReturnsOrderId() {
        // Arrange
        BigDecimal total = new BigDecimal("12.34");
        OrderId returnedId = new OrderId(ID);
        when(placeOrderUseCase.handle(any(PlaceOrderCommand.class), any())).thenReturn(Mono.just(returnedId));

        // Act + Assert
        webTestClient
                .post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PlaceOrderRequest(CUSTOMER, total))
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
        verify(placeOrderUseCase, times(1)).handle(cmdCaptor.capture(), any());
        PlaceOrderCommand usedCmd = cmdCaptor.getValue();
        assertThat(usedCmd.customerId().value()).isEqualTo(CUSTOMER);
        assertThat(usedCmd.total().amount()).isEqualByComparingTo(total);
    }

    @Test
    @DisplayName("POST /orders returns 400 when the customer id is missing")
    void createRejectsMissingCustomerId() {
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
                .bodyValue("{\"customerId\": \"11111111-2222-4333-8444-555555555555\", \"total\": -1.00}")
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(placeOrderUseCase);
    }

    @Test
    @DisplayName("POST /orders returns 400 when the customer id is not a canonical UUID")
    void malformedCustomerIdReturns400() {
        webTestClient
                .post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"customerId\": \"not-a-uuid\", \"total\": 10.00}")
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(placeOrderUseCase);
    }

    @Test
    @DisplayName("POST /orders returns 422 problem+json when the customer is unknown")
    void unknownCustomerReturns422() {
        when(placeOrderUseCase.handle(any(), any()))
                .thenReturn(Mono.error(new UnknownCustomerException(new CustomerId(CUSTOMER))));

        webTestClient
                .post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PlaceOrderRequest(CUSTOMER, new BigDecimal("1.00")))
                .exchange()
                .expectStatus()
                .isEqualTo(422)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(422);
    }

    @Test
    @DisplayName("POST /orders returns 422 problem+json when the credit limit would be exceeded")
    void creditLimitExceededReturns422() {
        when(placeOrderUseCase.handle(any(), any()))
                .thenReturn(Mono.error(new CreditLimitExceededException(new CustomerId(CUSTOMER))));

        webTestClient
                .post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PlaceOrderRequest(CUSTOMER, new BigDecimal("1.00")))
                .exchange()
                .expectStatus()
                .isEqualTo(422)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(422);
    }

    @Test
    @DisplayName("POST /orders propagates errors from the use case as 5xx by default")
    void createPropagatesUseCaseError() {
        when(placeOrderUseCase.handle(any(), any())).thenReturn(Mono.error(new IllegalStateException("boom")));

        webTestClient
                .post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PlaceOrderRequest(CUSTOMER, new BigDecimal("1.00")))
                .exchange()
                .expectStatus()
                .is5xxServerError();

        verify(placeOrderUseCase, times(1)).handle(any(), any());
    }

    @Test
    @DisplayName("GET /orders/{id} returns 200 with the order view when it exists")
    void getReturnsOrder() {
        Order placed = new Order(new CustomerId(CUSTOMER), new Money(new BigDecimal("42.00")));
        placed.place(); // NEW -> PLACED
        when(getOrderUseCase.findById(eq(new OrderId(ID)), any())).thenReturn(Mono.just(placed));

        webTestClient
                .get()
                .uri("/orders/{id}", ID)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.customerId")
                .isEqualTo(CUSTOMER)
                .jsonPath("$.status")
                .isEqualTo("PLACED");
    }

    @Test
    @DisplayName("GET /orders/{id} returns 404 problem+json when the order is unknown")
    void getReturnsNotFound() {
        when(getOrderUseCase.findById(eq(new OrderId(ID_2)), any())).thenReturn(Mono.empty());

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

    @Test
    @DisplayName("POST /orders passes actor type and correlation id from the headers to the use case and echoes the id")
    void createPassesUseCaseMetadataFromHeaders() {
        when(placeOrderUseCase.handle(any(), any())).thenReturn(Mono.just(new OrderId(ID)));

        webTestClient
                .post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Actor-Type", "BUSINESS_CUSTOMER")
                .header("X-Correlation-Id", "corr-42")
                .bodyValue(new PlaceOrderRequest(CUSTOMER, new BigDecimal("1.00")))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .valueEquals("X-Correlation-Id", "corr-42");

        ArgumentCaptor<UseCaseMetadata> metaCaptor = ArgumentCaptor.forClass(UseCaseMetadata.class);
        verify(placeOrderUseCase).handle(any(), metaCaptor.capture());
        assertThat(metaCaptor.getValue())
                .isEqualTo(new UseCaseMetadata(ActorType.BUSINESS_CUSTOMER, "rest", "corr-42"));
    }

    @Test
    @DisplayName("Without headers the caller is classified UNKNOWN and a correlation id is generated")
    void missingHeadersFallBackToUnknownAndGeneratedCorrelationId() {
        Order placed = new Order(new CustomerId(CUSTOMER), new Money(new BigDecimal("42.00")));
        when(getOrderUseCase.findById(eq(new OrderId(ID)), any())).thenReturn(Mono.just(placed));

        webTestClient
                .get()
                .uri("/orders/{id}", ID)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .exists("X-Correlation-Id");

        ArgumentCaptor<UseCaseMetadata> metaCaptor = ArgumentCaptor.forClass(UseCaseMetadata.class);
        verify(getOrderUseCase).findById(any(), metaCaptor.capture());
        assertThat(metaCaptor.getValue().actorType()).isEqualTo(ActorType.UNKNOWN);
        assertThat(metaCaptor.getValue().correlationId()).isNotBlank();
    }

    @Test
    @DisplayName("An actor type outside the contract enum is rejected with 400 before any use case runs")
    void unknownActorTypeValueIsRejected() {
        webTestClient
                .get()
                .uri("/orders/{id}", ID)
                .header("X-Actor-Type", "HACKER")
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(getOrderUseCase);
    }

    @Test
    @DisplayName("A correlation id with characters unsafe for logs is rejected with 400")
    void unsafeCorrelationIdIsRejected() {
        webTestClient
                .get()
                .uri("/orders/{id}", ID)
                .header("X-Correlation-Id", "bad id;INJECTED")
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(getOrderUseCase);
    }
}

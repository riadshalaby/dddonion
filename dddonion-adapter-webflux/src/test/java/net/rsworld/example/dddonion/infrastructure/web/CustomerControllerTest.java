package net.rsworld.example.dddonion.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import net.rsworld.example.dddonion.application.customer.usecase.ChangeCreditLimitUseCase;
import net.rsworld.example.dddonion.application.customer.usecase.GetCustomerUseCase;
import net.rsworld.example.dddonion.application.customer.usecase.RegisterCustomerUseCase;
import net.rsworld.example.dddonion.domain.common.ConcurrentAggregateUpdateException;
import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.command.ChangeCreditLimitCommand;
import net.rsworld.example.dddonion.domain.customer.command.RegisterCustomerCommand;
import net.rsworld.example.dddonion.domain.customer.model.Customer;
import net.rsworld.example.dddonion.domain.customer.model.CustomerEmail;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.domain.customer.model.CustomerNotFoundException;
import net.rsworld.example.dddonion.domain.customer.model.EmailAlreadyRegisteredException;
import net.rsworld.example.dddonion.infrastructure.web.dto.RegisterCustomerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;
import reactor.core.publisher.Mono;

class CustomerControllerTest {

    private static final String ID = "3f6a9c1e-4b2d-4f0a-9c7e-8a1b2c3d4e5f";

    private RegisterCustomerUseCase register;
    private GetCustomerUseCase get;
    private ChangeCreditLimitUseCase changeLimit;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        register = mock(RegisterCustomerUseCase.class);
        get = mock(GetCustomerUseCase.class);
        changeLimit = mock(ChangeCreditLimitUseCase.class);
        ProxyFactory proxy = new ProxyFactory(new CustomerController(register, get, changeLimit));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new MethodValidationInterceptor());
        client = WebTestClient.bindToController(proxy.getProxy())
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void registerReturns201WithLocation() {
        when(register.handle(any(), any())).thenReturn(Mono.just(new CustomerId(ID)));

        client.post()
                .uri("/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegisterCustomerRequest("a@example.com", new BigDecimal("500.00")))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .valueEquals("Location", "/customers/" + ID)
                .expectBody()
                .jsonPath("$.customerId")
                .isEqualTo(ID);

        var captor = ArgumentCaptor.forClass(RegisterCustomerCommand.class);
        verify(register).handle(captor.capture(), any());
        assertThat(captor.getValue().email().value()).isEqualTo("a@example.com");
        assertThat(captor.getValue().creditLimit().amount()).isEqualByComparingTo("500.00");
    }

    @Test
    void registerDuplicateEmailReturns409() {
        when(register.handle(any(), any()))
                .thenReturn(Mono.error(new EmailAlreadyRegisteredException(new CustomerEmail("a@example.com"))));

        client.post()
                .uri("/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegisterCustomerRequest("a@example.com", new BigDecimal("500.00")))
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void registerInvalidEmailReturns400() {
        client.post()
                .uri("/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\": \"a@b\", \"creditLimit\": 10}")
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(register);
    }

    @Test
    void getReturnsCustomerView() {
        var customer = new Customer(
                new CustomerId(ID), new CustomerEmail("a@example.com"), Money.of("500"), Money.of("20"), 3L);
        when(get.findById(eq(new CustomerId(ID)), any())).thenReturn(Mono.just(customer));

        client.get()
                .uri("/customers/{id}", ID)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.customerId")
                .isEqualTo(ID)
                .jsonPath("$.email")
                .isEqualTo("a@example.com")
                .jsonPath("$.creditLimit")
                .isEqualTo(500)
                .jsonPath("$.openAmount")
                .isEqualTo(20);
    }

    @Test
    void getUnknownCustomerReturns404() {
        when(get.findById(any(), any())).thenReturn(Mono.empty());

        client.get()
                .uri("/customers/{id}", ID)
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void getMalformedIdReturns400() {
        client.get().uri("/customers/{id}", "nope").exchange().expectStatus().isBadRequest();

        verifyNoInteractions(get);
    }

    @Test
    void changeCreditLimitReturns204() {
        when(changeLimit.handle(any(), any())).thenReturn(Mono.empty());

        client.put()
                .uri("/customers/{id}/credit-limit", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"creditLimit\": 750.50}")
                .exchange()
                .expectStatus()
                .isNoContent();

        var captor = ArgumentCaptor.forClass(ChangeCreditLimitCommand.class);
        verify(changeLimit).handle(captor.capture(), any());
        assertThat(captor.getValue().customerId()).isEqualTo(new CustomerId(ID));
        assertThat(captor.getValue().creditLimit().amount()).isEqualByComparingTo("750.50");
    }

    @Test
    void changeCreditLimitUnknownCustomerReturns404() {
        when(changeLimit.handle(any(), any()))
                .thenReturn(Mono.error(new CustomerNotFoundException(new CustomerId(ID))));

        client.put()
                .uri("/customers/{id}/credit-limit", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"creditLimit\": 10}")
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void changeCreditLimitNegativeReturns400() {
        client.put()
                .uri("/customers/{id}/credit-limit", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"creditLimit\": -1}")
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(changeLimit);
    }

    @Test
    void changeCreditLimitRetriesExhaustedReturns409() {
        when(changeLimit.handle(any(), any()))
                .thenReturn(Mono.error(new ConcurrentAggregateUpdateException("Customer", ID)));

        client.put()
                .uri("/customers/{id}/credit-limit", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"creditLimit\": 10}")
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(409);
    }
}

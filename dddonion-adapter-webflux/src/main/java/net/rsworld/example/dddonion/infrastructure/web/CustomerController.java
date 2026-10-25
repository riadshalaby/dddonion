package net.rsworld.example.dddonion.infrastructure.web;

import java.net.URI;
import net.rsworld.example.dddonion.application.customer.usecase.ChangeCreditLimitUseCase;
import net.rsworld.example.dddonion.application.customer.usecase.GetCustomerUseCase;
import net.rsworld.example.dddonion.application.customer.usecase.RegisterCustomerUseCase;
import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.command.ChangeCreditLimitCommand;
import net.rsworld.example.dddonion.domain.customer.command.RegisterCustomerCommand;
import net.rsworld.example.dddonion.domain.customer.model.Customer;
import net.rsworld.example.dddonion.domain.customer.model.CustomerEmail;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.domain.customer.model.CustomerNotFoundException;
import net.rsworld.example.dddonion.infrastructure.web.api.CustomersApi;
import net.rsworld.example.dddonion.infrastructure.web.dto.ActorType;
import net.rsworld.example.dddonion.infrastructure.web.dto.ChangeCreditLimitRequest;
import net.rsworld.example.dddonion.infrastructure.web.dto.CustomerResponse;
import net.rsworld.example.dddonion.infrastructure.web.dto.CustomerView;
import net.rsworld.example.dddonion.infrastructure.web.dto.RegisterCustomerRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** REST adapter implementing the {@link CustomersApi} interface generated from {@code openapi/api.yaml}. */
@RestController
public class CustomerController implements CustomersApi {

    private final RegisterCustomerUseCase registerCustomer;
    private final GetCustomerUseCase getCustomer;
    private final ChangeCreditLimitUseCase changeCreditLimit;

    public CustomerController(
            RegisterCustomerUseCase registerCustomer,
            GetCustomerUseCase getCustomer,
            ChangeCreditLimitUseCase changeCreditLimit) {
        this.registerCustomer = registerCustomer;
        this.getCustomer = getCustomer;
        this.changeCreditLimit = changeCreditLimit;
    }

    @Override
    public Mono<ResponseEntity<CustomerResponse>> registerCustomer(
            Mono<RegisterCustomerRequest> registerCustomerRequest,
            ActorType xActorType,
            String xCorrelationId,
            ServerWebExchange exchange) {
        var metadata = UseCaseMetadataFactory.fromHeaders(xActorType, xCorrelationId);
        return registerCustomerRequest
                .map(req ->
                        new RegisterCustomerCommand(new CustomerEmail(req.getEmail()), new Money(req.getCreditLimit())))
                .flatMap(cmd -> registerCustomer.handle(cmd, metadata))
                .map(CustomerId::value)
                .map(id -> ResponseEntity.created(URI.create("/customers/" + id))
                        .header(UseCaseMetadataFactory.CORRELATION_ID_HEADER, metadata.correlationId())
                        .body(new CustomerResponse(id)));
    }

    @Override
    public Mono<ResponseEntity<CustomerView>> getCustomer(
            String customerId, ActorType xActorType, String xCorrelationId, ServerWebExchange exchange) {
        var metadata = UseCaseMetadataFactory.fromHeaders(xActorType, xCorrelationId);
        var id = new CustomerId(customerId);
        return getCustomer
                .findById(id, metadata)
                .map(CustomerController::toView)
                .map(view -> ResponseEntity.ok()
                        .header(UseCaseMetadataFactory.CORRELATION_ID_HEADER, metadata.correlationId())
                        .body(view))
                .switchIfEmpty(Mono.error(() -> new CustomerNotFoundException(id)));
    }

    @Override
    public Mono<ResponseEntity<Void>> changeCreditLimit(
            String customerId,
            Mono<ChangeCreditLimitRequest> changeCreditLimitRequest,
            ActorType xActorType,
            String xCorrelationId,
            ServerWebExchange exchange) {
        var metadata = UseCaseMetadataFactory.fromHeaders(xActorType, xCorrelationId);
        var id = new CustomerId(customerId);
        return changeCreditLimitRequest
                .map(req -> new ChangeCreditLimitCommand(id, new Money(req.getCreditLimit())))
                .flatMap(cmd -> changeCreditLimit.handle(cmd, metadata))
                .then(Mono.fromSupplier(() -> ResponseEntity.noContent()
                        .header(UseCaseMetadataFactory.CORRELATION_ID_HEADER, metadata.correlationId())
                        .<Void>build()));
    }

    private static CustomerView toView(Customer customer) {
        return new CustomerView(
                customer.id().value(),
                customer.email().value(),
                customer.creditLimit().amount(),
                customer.openAmount().amount());
    }
}

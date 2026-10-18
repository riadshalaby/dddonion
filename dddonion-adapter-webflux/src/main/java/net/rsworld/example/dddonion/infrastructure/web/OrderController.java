package net.rsworld.example.dddonion.infrastructure.web;

import java.net.URI;
import java.util.UUID;
import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.application.order.usecase.GetOrderUseCase;
import net.rsworld.example.dddonion.application.order.usecase.PlaceOrderUseCase;
import net.rsworld.example.dddonion.domain.order.command.PlaceOrderCommand;
import net.rsworld.example.dddonion.domain.order.model.CustomerEmail;
import net.rsworld.example.dddonion.domain.order.model.Money;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import net.rsworld.example.dddonion.infrastructure.web.api.OrdersApi;
import net.rsworld.example.dddonion.infrastructure.web.dto.ActorType;
import net.rsworld.example.dddonion.infrastructure.web.dto.OrderResponse;
import net.rsworld.example.dddonion.infrastructure.web.dto.OrderView;
import net.rsworld.example.dddonion.infrastructure.web.dto.PlaceOrderRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * REST adapter implementing the {@link OrdersApi} interface generated from
 * {@code src/main/resources/openapi/orders.yaml} (the design-first contract).
 */
@RestController
public class OrderController implements OrdersApi {
    static final String CHANNEL = "rest";
    static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private final PlaceOrderUseCase placeOrder;
    private final GetOrderUseCase getOrder;

    public OrderController(PlaceOrderUseCase placeOrder, GetOrderUseCase getOrder) {
        this.placeOrder = placeOrder;
        this.getOrder = getOrder;
    }

    @Override
    public Mono<ResponseEntity<OrderResponse>> placeOrder(
            Mono<PlaceOrderRequest> placeOrderRequest,
            ActorType xActorType,
            String xCorrelationId,
            ServerWebExchange exchange) {
        UseCaseMetadata metadata = metadata(xActorType, xCorrelationId);
        return placeOrderRequest
                .map(req -> new PlaceOrderCommand(new CustomerEmail(req.getCustomerEmail()), new Money(req.getTotal())))
                .flatMap(cmd -> placeOrder.handle(cmd, metadata))
                .map(OrderId::value)
                .map(id -> ResponseEntity.created(URI.create("/orders/" + id))
                        .header(CORRELATION_ID_HEADER, metadata.correlationId())
                        .body(new OrderResponse(id)));
    }

    @Override
    public Mono<ResponseEntity<OrderView>> getOrder(
            String orderId, ActorType xActorType, String xCorrelationId, ServerWebExchange exchange) {
        UseCaseMetadata metadata = metadata(xActorType, xCorrelationId);
        return getOrder.findById(new OrderId(orderId), metadata)
                .map(OrderController::toView)
                .map(view -> ResponseEntity.ok()
                        .header(CORRELATION_ID_HEADER, metadata.correlationId())
                        .body(view))
                .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId)));
    }

    /** Wire values are validated by the contract; a missing actor type is recorded as UNKNOWN, not rejected. */
    private static UseCaseMetadata metadata(ActorType actorType, String correlationId) {
        var classified = actorType == null
                ? net.rsworld.example.dddonion.application.context.ActorType.UNKNOWN
                : net.rsworld.example.dddonion.application.context.ActorType.valueOf(actorType.name());
        var correlation = correlationId == null ? UUID.randomUUID().toString() : correlationId;
        return new UseCaseMetadata(classified, CHANNEL, correlation);
    }

    private static OrderView toView(Order order) {
        return new OrderView(
                order.id().value(),
                order.customerEmail().value(),
                order.total().amount(),
                OrderView.StatusEnum.fromValue(order.status().name()));
    }
}

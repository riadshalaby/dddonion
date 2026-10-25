package net.rsworld.example.dddonion.infrastructure.web;

import java.net.URI;
import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.application.order.usecase.GetOrderUseCase;
import net.rsworld.example.dddonion.application.order.usecase.PlaceOrderUseCase;
import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.domain.order.command.PlaceOrderCommand;
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
 * REST adapter implementing the {@link OrdersApi} interface generated from {@code src/main/resources/openapi/api.yaml}
 * (the design-first contract).
 */
@RestController
public class OrderController implements OrdersApi {
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
        UseCaseMetadata metadata = UseCaseMetadataFactory.fromHeaders(xActorType, xCorrelationId);
        return placeOrderRequest
                .map(req -> new PlaceOrderCommand(new CustomerId(req.getCustomerId()), new Money(req.getTotal())))
                .flatMap(cmd -> placeOrder.handle(cmd, metadata))
                .map(OrderId::value)
                .map(id -> ResponseEntity.created(URI.create("/orders/" + id))
                        .header(UseCaseMetadataFactory.CORRELATION_ID_HEADER, metadata.correlationId())
                        .body(new OrderResponse(id)));
    }

    @Override
    public Mono<ResponseEntity<OrderView>> getOrder(
            String orderId, ActorType xActorType, String xCorrelationId, ServerWebExchange exchange) {
        UseCaseMetadata metadata = UseCaseMetadataFactory.fromHeaders(xActorType, xCorrelationId);
        return getOrder.findById(new OrderId(orderId), metadata)
                .map(OrderController::toView)
                .map(view -> ResponseEntity.ok()
                        .header(UseCaseMetadataFactory.CORRELATION_ID_HEADER, metadata.correlationId())
                        .body(view))
                .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId)));
    }

    private static OrderView toView(Order order) {
        return new OrderView(
                order.id().value(),
                order.customerId().value(),
                order.total().amount(),
                OrderView.StatusEnum.fromValue(order.status().name()));
    }
}

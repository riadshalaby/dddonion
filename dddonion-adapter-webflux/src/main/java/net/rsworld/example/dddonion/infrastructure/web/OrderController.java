package net.rsworld.example.dddonion.infrastructure.web;

import java.net.URI;
import net.rsworld.example.dddonion.application.order.usecase.GetOrderUseCase;
import net.rsworld.example.dddonion.application.order.usecase.PlaceOrderUseCase;
import net.rsworld.example.dddonion.domain.order.command.PlaceOrderCommand;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import net.rsworld.example.dddonion.infrastructure.web.api.OrdersApi;
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
    private final PlaceOrderUseCase placeOrder;
    private final GetOrderUseCase getOrder;

    public OrderController(PlaceOrderUseCase placeOrder, GetOrderUseCase getOrder) {
        this.placeOrder = placeOrder;
        this.getOrder = getOrder;
    }

    @Override
    public Mono<ResponseEntity<OrderResponse>> placeOrder(
            Mono<PlaceOrderRequest> placeOrderRequest, ServerWebExchange exchange) {
        return placeOrderRequest
                .map(req -> new PlaceOrderCommand(req.getCustomerEmail(), req.getTotal()))
                .flatMap(placeOrder::handle)
                .map(OrderId::value)
                .map(id -> ResponseEntity.created(URI.create("/orders/" + id)).body(new OrderResponse(id)));
    }

    @Override
    public Mono<ResponseEntity<OrderView>> getOrder(String orderId, ServerWebExchange exchange) {
        return getOrder.findById(new OrderId(orderId))
                .map(OrderController::toView)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId)));
    }

    private static OrderView toView(Order order) {
        return new OrderView(
                order.id().value(),
                order.customerEmail(),
                order.total(),
                OrderView.StatusEnum.fromValue(order.status().name()));
    }
}

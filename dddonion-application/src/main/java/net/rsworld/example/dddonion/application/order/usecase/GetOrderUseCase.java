package net.rsworld.example.dddonion.application.order.usecase;

import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import reactor.core.publisher.Mono;

/** Query use case: resolves an order by id. An empty result signals "not found". */
public interface GetOrderUseCase {
    Mono<Order> findById(OrderId id, UseCaseMetadata metadata);
}

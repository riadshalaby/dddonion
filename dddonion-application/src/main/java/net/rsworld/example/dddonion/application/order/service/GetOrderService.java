package net.rsworld.example.dddonion.application.order.service;

import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.application.order.usecase.GetOrderUseCase;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import net.rsworld.example.dddonion.domain.order.repository.OrderRepository;
import reactor.core.publisher.Mono;

/** Application service: adapts the {@code CompletionStage<Optional<Order>>} port to a reactive query. */
public class GetOrderService implements GetOrderUseCase {

    private final OrderRepository orders;

    public GetOrderService(OrderRepository orders) {
        this.orders = orders;
    }

    @Override
    public Mono<Order> findById(OrderId id, UseCaseMetadata metadata) {
        return Mono.fromCompletionStage(() -> orders.findById(id))
                .flatMap(found -> found.map(Mono::just).orElseGet(Mono::empty));
    }
}

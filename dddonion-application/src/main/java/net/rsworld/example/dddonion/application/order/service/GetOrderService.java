package net.rsworld.example.dddonion.application.order.service;

import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.application.order.usecase.GetOrderUseCase;
import net.rsworld.example.dddonion.architecture.ApplicationComponent;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import net.rsworld.example.dddonion.domain.order.repository.OrderRepository;
import reactor.core.publisher.Mono;

@ApplicationComponent
class GetOrderService implements GetOrderUseCase {

    private final OrderRepository orders;

    GetOrderService(OrderRepository orders) {
        this.orders = orders;
    }

    @Override
    public Mono<Order> findById(OrderId id, UseCaseMetadata metadata) {
        return orders.findById(id);
    }
}

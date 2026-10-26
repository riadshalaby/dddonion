package net.rsworld.example.dddonion.domain.order.repository;

import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import reactor.core.publisher.Mono;

/** Domain-Port: reactive and lazy, work starts on subscription. Not-Found is signaled by an empty {@link Mono}. */
public interface OrderRepository {

    Mono<Order> save(Order order);

    Mono<Order> findById(OrderId id);
}

package net.rsworld.example.dddonion.domain.order.repository;

import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import reactor.core.publisher.Mono;

/**
 * Domain-Port: reaktiv und lazy, die Arbeit beginnt erst mit der Subscription. „Nicht gefunden“ wird durch einen leeren
 * {@link Mono} signalisiert.
 */
public interface OrderRepository {

    Mono<Order> save(Order order);

    Mono<Order> findById(OrderId id);
}

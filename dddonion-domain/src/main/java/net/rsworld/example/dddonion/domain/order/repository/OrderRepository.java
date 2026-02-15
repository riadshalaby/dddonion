package net.rsworld.example.dddonion.domain.order.repository;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;

/** Domain-Port: framework-free (JDK types). Not-Found is signaled via Optional. */
public interface OrderRepository {

    CompletionStage<Order> save(Order order);

    CompletionStage<Optional<Order>> findById(OrderId id);
}

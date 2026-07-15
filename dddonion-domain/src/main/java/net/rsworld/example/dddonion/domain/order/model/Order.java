package net.rsworld.example.dddonion.domain.order.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.rsworld.example.dddonion.domain.common.DomainEvent;
import net.rsworld.example.dddonion.domain.order.event.OrderPlaced;

public class Order {
    private final OrderId id;
    private OrderStatus status;
    private final CustomerEmail customerEmail;
    private final Money total;
    private long version; // domain version used as event sequence
    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    public Order(CustomerEmail customerEmail, Money total) {
        this(OrderId.newId(), customerEmail, total, 0L, OrderStatus.NEW);
    }

    public Order(OrderId id, CustomerEmail customerEmail, Money total, long version, OrderStatus status) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.customerEmail = Objects.requireNonNull(customerEmail, "customerEmail must not be null");
        this.total = Objects.requireNonNull(total, "total must not be null");
        this.version = version;
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public void place() {
        if (status != OrderStatus.NEW) throw new IllegalStateException("Order already progressed");
        status = OrderStatus.PLACED;
        long nextSeq = version + 1;
        pendingEvents.add(new OrderPlaced(id.value(), nextSeq, Instant.now(), customerEmail.value(), total.amount()));
        version = nextSeq;
    }

    public void pay() {
        if (status != OrderStatus.PLACED) throw new IllegalStateException("Only placed orders can be paid");
        status = OrderStatus.PAID;
        version = version + 1;
    }

    public List<DomainEvent> pullEvents() {
        var copy = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return copy;
    }

    public OrderId id() {
        return id;
    }

    public long version() {
        return version;
    }

    public OrderStatus status() {
        return status;
    }

    public CustomerEmail customerEmail() {
        return customerEmail;
    }

    public Money total() {
        return total;
    }
}

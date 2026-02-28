package net.rsworld.example.dddonion.domain.order.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.rsworld.example.dddonion.domain.common.DomainEvent;
import net.rsworld.example.dddonion.domain.order.event.OrderPlaced;

public class Order {
    private final OrderId id;
    private OrderStatus status;
    private final String customerEmail;
    private final BigDecimal total;
    private long version; // domain version used as event sequence
    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    public Order(String customerEmail, BigDecimal total) {
        this.id = OrderId.newId();
        this.customerEmail = customerEmail;
        this.total = total;
        this.version = 0L;
        this.status = OrderStatus.NEW;
    }

    public Order(OrderId id, String customerEmail, BigDecimal total, long version, OrderStatus status) {
        this.id = id;
        this.customerEmail = customerEmail;
        this.total = total;
        this.version = version;
        this.status = status;
    }

    public void place() {
        if (status != OrderStatus.NEW) throw new IllegalStateException("Order already progressed");
        status = OrderStatus.PLACED;
        long nextSeq = version + 1;
        pendingEvents.add(new OrderPlaced(id.value(), nextSeq, Instant.now(), customerEmail, total));
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

    public String customerEmail() {
        return customerEmail;
    }

    public BigDecimal total() {
        return total;
    }
}

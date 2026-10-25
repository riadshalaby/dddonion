package net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.mapper;

import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import net.rsworld.example.dddonion.domain.order.model.OrderStatus;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.dto.OrderRow;

public final class OrderMapper {

    private OrderMapper() {}

    public static OrderRow toRow(Order o) {
        OrderRow r = new OrderRow();
        r.setId(o.id().value());
        r.setCustomerId(o.customerId().value());
        r.setTotal(o.total().amount());
        r.setStatus(o.status().name());
        r.setVersion(o.version());
        return r;
    }

    public static Order toDomain(OrderRow r) {
        long version = r.getVersion() == null ? 0L : r.getVersion();
        return new Order(
                new OrderId(r.getId()),
                new CustomerId(r.getCustomerId()),
                new Money(r.getTotal()),
                version,
                OrderStatus.valueOf(r.getStatus()));
    }
}

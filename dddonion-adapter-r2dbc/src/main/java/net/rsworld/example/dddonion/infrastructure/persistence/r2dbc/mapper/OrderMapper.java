package net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.mapper;

import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import net.rsworld.example.dddonion.domain.order.model.OrderStatus;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.dto.OrderRow;

public final class OrderMapper {

    private OrderMapper() {}

    public static OrderRow toRow(Order o) {
        OrderRow r = new OrderRow();
        r.setId(o.id().value());
        r.setCustomerEmail(o.customerEmail());
        r.setTotal(o.total());
        r.setStatus(o.status().name());
        r.setVersion(o.version()); // will be set to null in the adapter if necessary (INSERT)
        return r;
    }

    public static Order toDomain(OrderRow r) {
        long version = r.getVersion() == null ? 0L : r.getVersion();
        return new Order(
                new OrderId(r.getId()),
                r.getCustomerEmail(),
                r.getTotal(),
                version,
                OrderStatus.valueOf(r.getStatus()));
    }
}

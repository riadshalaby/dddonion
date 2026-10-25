package net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import net.rsworld.example.dddonion.domain.order.model.OrderStatus;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.dto.OrderRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderMapperTest {

    private static final String ID = "3f6a9c1e-4b2d-4f0a-9c7e-8a1b2c3d4e5f";
    private static final String CUST = "11111111-2222-4333-8444-555555555555";
    private static final String CUST_2 = "66666666-7777-4888-9999-000000000000";
    private static final String ID_2 = "7a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";

    @Test
    @DisplayName("Mappt alle Felder einer Domain-Order korrekt auf eine OrderRow")
    void toRow_maps_all_fields_from_domain_order() {
        Order order = new Order(
                new OrderId(ID), new CustomerId(CUST), new Money(new BigDecimal("42.50")), 7L, OrderStatus.PLACED);

        OrderRow row = OrderMapper.toRow(order);

        assertEquals(ID, row.getId());
        assertEquals(CUST, row.getCustomerId());
        assertEquals(new BigDecimal("42.50"), row.getTotal());
        assertEquals("PLACED", row.getStatus());
        assertEquals(7L, row.getVersion());
    }

    @Test
    @DisplayName("Setzt beim Mapping auf Domain die Version auf 0, wenn sie in der Row null ist")
    void toDomain_defaults_null_row_version_to_zero() {
        OrderRow row = new OrderRow(ID_2, CUST_2, new BigDecimal("15.00"), "NEW", null, null, null);

        Order order = OrderMapper.toDomain(row);

        assertEquals(ID_2, order.id().value());
        assertEquals(CUST_2, order.customerId().value());
        assertEquals(new BigDecimal("15.00"), order.total().amount());
        assertEquals(OrderStatus.NEW, order.status());
        assertEquals(0L, order.version());
    }
}

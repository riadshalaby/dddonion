package net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import net.rsworld.example.dddonion.domain.order.model.OrderStatus;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.dto.OrderRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderMapperTest {

    @Test
    @DisplayName("Mappt alle Felder einer Domain-Order korrekt auf eine OrderRow")
    void toRow_maps_all_fields_from_domain_order() {
        Order order = new Order(
                new OrderId("ord-123"), "john.doe@example.com", new BigDecimal("42.50"), 7L, OrderStatus.PLACED);

        OrderRow row = OrderMapper.toRow(order);

        assertEquals("ord-123", row.getId());
        assertEquals("john.doe@example.com", row.getCustomerEmail());
        assertEquals(new BigDecimal("42.50"), row.getTotal());
        assertEquals("PLACED", row.getStatus());
        assertEquals(7L, row.getVersion());
    }

    @Test
    @DisplayName("Setzt beim Mapping auf Domain die Version auf 0, wenn sie in der Row null ist")
    void toDomain_defaults_null_row_version_to_zero() {
        OrderRow row = new OrderRow("ord-456", "alice@example.com", new BigDecimal("15.00"), "NEW", null);

        Order order = OrderMapper.toDomain(row);

        assertEquals("ord-456", order.id().value());
        assertEquals("alice@example.com", order.customerEmail());
        assertEquals(new BigDecimal("15.00"), order.total());
        assertEquals(OrderStatus.NEW, order.status());
        assertEquals(0L, order.version());
    }
}

package net.rsworld.example.dddonion.domain.order;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import net.rsworld.example.dddonion.domain.common.DomainEvent;
import net.rsworld.example.dddonion.domain.order.event.OrderPlaced;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderTest {

    @Test
    @DisplayName("Setzt beim Place-Vorgang Status und Version und erzeugt genau ein OrderPlaced-Event")
    void place_shouldCreateEventAndBumpVersion() {
        Order order = new Order("a@b.com", new BigDecimal("10.50"));
        order.place();

        List<DomainEvent> events = order.pullEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(OrderPlaced.class);
        OrderPlaced placed = (OrderPlaced) events.get(0);
        assertThat(placed.sequence()).isEqualTo(1L);
        assertThat(order.version()).isEqualTo(1L);
        assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
    }

    @Test
    @DisplayName("Verhindert ein erneutes Place auf einer bereits platzierten Order")
    void place_twiceShouldFail() {
        Order order = new Order("a@b.com", new BigDecimal("10.50"));
        order.place();
        assertThatThrownBy(order::place).isInstanceOf(IllegalStateException.class);
    }
}

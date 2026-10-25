package net.rsworld.example.dddonion.domain.order;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import net.rsworld.example.dddonion.domain.common.DomainEvent;
import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.domain.order.event.OrderPlaced;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import net.rsworld.example.dddonion.domain.order.model.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderTest {

    @Test
    @DisplayName("Setzt beim Place-Vorgang Status und Version und erzeugt genau ein OrderPlaced-Event")
    void place_shouldCreateEventAndBumpVersion() {
        CustomerId customerId = CustomerId.newId();
        Order order = new Order(customerId, new Money(new BigDecimal("10.50")));
        order.place();

        List<DomainEvent> events = order.pullEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(OrderPlaced.class);
        OrderPlaced placed = (OrderPlaced) events.get(0);
        assertThat(placed.sequence()).isEqualTo(1L);
        assertThat(placed.customerId()).isEqualTo(customerId.value());
        assertThat(order.customerId()).isEqualTo(customerId);
        assertThat(order.version()).isEqualTo(1L);
        assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
    }

    @Test
    @DisplayName("Verhindert ein erneutes Place auf einer bereits platzierten Order")
    void place_twiceShouldFail() {
        Order order = new Order(CustomerId.newId(), new Money(new BigDecimal("10.50")));
        order.place();
        assertThatThrownBy(order::place).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Setzt beim Pay-Vorgang Status auf PAID und erhöht die Version")
    void pay_shouldSetPaidStatusAndBumpVersion() {
        Order order = new Order(CustomerId.newId(), new Money(new BigDecimal("10.50")));
        order.place();
        long versionAfterPlace = order.version();

        order.pay();

        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.version()).isEqualTo(versionAfterPlace + 1);
    }

    @Test
    @DisplayName("Verhindert Pay auf einer Order die noch nicht placed wurde")
    void pay_onNewOrderShouldFail() {
        Order order = new Order(CustomerId.newId(), new Money(new BigDecimal("10.50")));
        assertThatThrownBy(order::pay).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Merkt sich die geladene Version, auch wenn Übergänge die Version erhöhen")
    void loadedVersion_staysAtReconstitutedVersion() {
        Order order = new Order(
                OrderId.newId(), CustomerId.newId(), new Money(new BigDecimal("10.50")), 3L, OrderStatus.PLACED);

        order.pay();

        assertThat(order.version()).isEqualTo(4L);
        assertThat(order.loadedVersion()).isEqualTo(3L);
    }
}

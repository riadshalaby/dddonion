package net.rsworld.example.dddonion.domain.order.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PlaceOrderCommand invariants")
class PlaceOrderCommandTest {

    // Format/range rules are covered by CustomerIdTest and MoneyTest; the command only guards presence,
    // because its fields are already-validated value objects (no duplicated validation here).

    @Test
    @DisplayName("Creates a command from valid value objects")
    void createsFromValidValueObjects() {
        var customerId = CustomerId.newId();
        var total = Money.of("10.50");

        var cmd = new PlaceOrderCommand(customerId, total);

        assertThat(cmd.customerId()).isEqualTo(customerId);
        assertThat(cmd.total()).isEqualTo(total);
    }

    @Test
    @DisplayName("Rejects a null customer id")
    void rejectsNullCustomerId() {
        assertThatThrownBy(() -> new PlaceOrderCommand(null, Money.of("10.50")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("customerId");
    }

    @Test
    @DisplayName("Rejects a null total")
    void rejectsNullTotal() {
        assertThatThrownBy(() -> new PlaceOrderCommand(CustomerId.newId(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("total");
    }
}

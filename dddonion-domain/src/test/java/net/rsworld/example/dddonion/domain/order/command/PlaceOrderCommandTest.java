package net.rsworld.example.dddonion.domain.order.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.rsworld.example.dddonion.domain.order.model.CustomerEmail;
import net.rsworld.example.dddonion.domain.order.model.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PlaceOrderCommand invariants")
class PlaceOrderCommandTest {

    // Format/range rules are covered by CustomerEmailTest and MoneyTest; the command only guards presence,
    // because its fields are already-validated value objects (no duplicated validation here).

    @Test
    @DisplayName("Creates a command from valid value objects")
    void createsFromValidValueObjects() {
        var email = new CustomerEmail("test@example.com");
        var total = Money.of("10.50");

        var cmd = new PlaceOrderCommand(email, total);

        assertThat(cmd.customerEmail()).isEqualTo(email);
        assertThat(cmd.total()).isEqualTo(total);
    }

    @Test
    @DisplayName("Rejects a null customer e-mail")
    void rejectsNullEmail() {
        assertThatThrownBy(() -> new PlaceOrderCommand(null, Money.of("10.50")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("customerEmail");
    }

    @Test
    @DisplayName("Rejects a null total")
    void rejectsNullTotal() {
        assertThatThrownBy(() -> new PlaceOrderCommand(new CustomerEmail("test@example.com"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("total");
    }
}

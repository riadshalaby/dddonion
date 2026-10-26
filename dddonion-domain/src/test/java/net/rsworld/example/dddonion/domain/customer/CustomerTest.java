package net.rsworld.example.dddonion.domain.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.model.CreditLimitExceededException;
import net.rsworld.example.dddonion.domain.customer.model.Customer;
import net.rsworld.example.dddonion.domain.customer.model.CustomerEmail;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Customer credit invariants")
class CustomerTest {

    private static final CustomerEmail EMAIL = new CustomerEmail("jane@example.com");

    private static Customer customer(String limit, String open) {
        return new Customer(CustomerId.newId(), EMAIL, Money.of(limit), Money.of(open), 0L);
    }

    @Test
    @DisplayName("A new customer has no open amount and version 0")
    void newCustomerHasNoOpenAmount() {
        Customer c = new Customer(EMAIL, Money.of("100"));

        assertThat(c.openAmount()).isEqualTo(Money.zero());
        assertThat(c.version()).isZero();
        assertThat(c.id()).isNotNull();
        assertThat(c.email()).isEqualTo(EMAIL);
        assertThat(c.creditLimit()).isEqualTo(Money.of("100"));
    }

    @Test
    @DisplayName("Reserves credit up to exactly the limit and leaves the version untouched")
    void reservesCreditUpToExactlyTheLimit() {
        Customer c = customer("100", "40");

        c.reserveCredit(Money.of("60"));

        assertThat(c.openAmount().amount()).isEqualByComparingTo("100");
        assertThat(c.version()).isZero();
    }

    @Test
    @DisplayName("Rejects a reservation above the limit and leaves the open amount unchanged")
    void rejectsReservationAboveTheLimitUnchanged() {
        Customer c = customer("100", "40");

        assertThatThrownBy(() -> c.reserveCredit(Money.of("60.01"))).isInstanceOf(CreditLimitExceededException.class);

        assertThat(c.openAmount().amount()).isEqualByComparingTo("40");
    }

    @Test
    @DisplayName("Lowering the limit below the open amount is allowed but blocks new orders")
    void loweringLimitBelowOpenAmountIsAllowedButBlocksNewOrders() {
        Customer c = customer("100", "80");

        assertThatCode(() -> c.changeCreditLimit(Money.of("50"))).doesNotThrowAnyException();

        assertThat(c.creditLimit()).isEqualTo(Money.of("50"));
        assertThatThrownBy(() -> c.reserveCredit(Money.of("0.01"))).isInstanceOf(CreditLimitExceededException.class);
    }
}

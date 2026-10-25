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
    @DisplayName("Allows an order up to exactly the limit")
    void allowsOrderUpToExactlyTheLimit() {
        Customer c = customer("100", "40");

        assertThatCode(() -> c.ensureCreditFor(Money.of("60"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Rejects an order above the limit")
    void rejectsOrderAboveTheLimit() {
        Customer c = customer("100", "40");

        assertThatThrownBy(() -> c.ensureCreditFor(Money.of("60.01"))).isInstanceOf(CreditLimitExceededException.class);
    }

    @Test
    @DisplayName("recordOrder adds to the open amount and leaves the version untouched")
    void recordOrderAddsToOpenAmount() {
        Customer c = customer("100", "40");

        c.recordOrder(Money.of("25"));

        assertThat(c.openAmount().amount()).isEqualByComparingTo("65");
        assertThat(c.version()).isZero();
    }

    @Test
    @DisplayName("Lowering the limit below the open amount is allowed but blocks new orders")
    void loweringLimitBelowOpenAmountIsAllowedButBlocksNewOrders() {
        Customer c = customer("100", "80");

        assertThatCode(() -> c.changeCreditLimit(Money.of("50"))).doesNotThrowAnyException();

        assertThat(c.creditLimit()).isEqualTo(Money.of("50"));
        assertThatThrownBy(() -> c.ensureCreditFor(Money.of("0.01"))).isInstanceOf(CreditLimitExceededException.class);
    }
}

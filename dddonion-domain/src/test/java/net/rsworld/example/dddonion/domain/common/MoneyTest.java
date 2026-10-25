package net.rsworld.example.dddonion.domain.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Money invariants")
class MoneyTest {

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.01", "12.34", "999999999.99"})
    @DisplayName("Accepts non-negative amounts")
    void acceptsNonNegative(String amount) {
        assertThat(new Money(new BigDecimal(amount)).amount()).isEqualByComparingTo(amount);
    }

    @Test
    @DisplayName("Rejects null amount")
    void rejectsNull() {
        assertThatThrownBy(() -> new Money(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "-1", "-10.50", "-999.99"})
    @DisplayName("Rejects negative amounts")
    void rejectsNegative(String amount) {
        assertThatThrownBy(() -> new Money(new BigDecimal(amount)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be >= 0");
    }

    @Test
    @DisplayName("zero() is an amount of zero")
    void zeroIsZero() {
        assertThat(Money.zero().amount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("plus() adds two amounts")
    void plusAdds() {
        assertThat(Money.of("40").plus(Money.of("25.50")).amount()).isEqualByComparingTo("65.50");
    }

    @Test
    @DisplayName("isGreaterThan() compares by value, ignoring scale")
    void isGreaterThanComparesByValue() {
        assertThat(Money.of("0.01").isGreaterThan(Money.zero())).isTrue();
        assertThat(Money.of("100.00").isGreaterThan(Money.of("100"))).isFalse();
        assertThat(Money.of("1").isGreaterThan(Money.of("2"))).isFalse();
    }

    @Test
    @DisplayName("equals() and hashCode() compare by value, ignoring scale")
    void equalityIgnoresScale() {
        assertThat(Money.of("100")).isEqualTo(Money.of("100.00"));
        assertThat(Money.of("100")).hasSameHashCodeAs(Money.of("100.00"));
        assertThat(Money.zero()).isEqualTo(Money.of("0.00"));
        assertThat(Money.zero()).hasSameHashCodeAs(Money.of("0.00"));
        assertThat(Money.of("100.01")).isNotEqualTo(Money.of("100"));
    }

    @Test
    @DisplayName("Keeps the amount as given (no scale normalisation)")
    void keepsAmountAsGiven() {
        assertThat(Money.of("12.30").amount()).isEqualTo(new BigDecimal("12.30"));
    }
}

package net.rsworld.example.dddonion.domain.order.model;

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
}

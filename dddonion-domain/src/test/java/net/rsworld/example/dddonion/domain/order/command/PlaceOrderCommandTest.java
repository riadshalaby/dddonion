package net.rsworld.example.dddonion.domain.order.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("PlaceOrderCommand invariants")
class PlaceOrderCommandTest {

    @Nested
    @DisplayName("Valid command creation")
    class ValidCommandCreation {

        @Test
        @DisplayName("Should create command with valid email and positive total")
        void shouldCreateWithValidEmailAndPositiveTotal() {
            var cmd = new PlaceOrderCommand("test@example.com", new BigDecimal("10.50"));

            assertThat(cmd.customerEmail()).isEqualTo("test@example.com");
            assertThat(cmd.total()).isEqualByComparingTo("10.50");
        }

        @Test
        @DisplayName("Should accept zero total")
        void shouldAcceptZeroTotal() {
            var cmd = new PlaceOrderCommand("test@example.com", BigDecimal.ZERO);

            assertThat(cmd.total()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "simple@example.com",
                    "user.name@example.com",
                    "user+tag@example.co.uk",
                    "user_name@sub.example.com",
                    "123@example.com",
                    "a@b.co"
                })
        @DisplayName("Should accept various valid email formats")
        void shouldAcceptValidEmailFormats(String email) {
            var cmd = new PlaceOrderCommand(email, BigDecimal.TEN);

            assertThat(cmd.customerEmail()).isEqualTo(email);
        }
    }

    @Nested
    @DisplayName("Email invariant violations")
    class EmailInvariantViolations {

        @Test
        @DisplayName("Should throw when email is null")
        void shouldThrowWhenEmailIsNull() {
            assertThatThrownBy(() -> new PlaceOrderCommand(null, new BigDecimal("10.50")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Customer email must not be null or empty");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n", "    \t\n"})
        @DisplayName("Should throw when email is null, empty or blank")
        void shouldThrowWhenEmailIsNullEmptyOrBlank(String email) {
            assertThatThrownBy(() -> new PlaceOrderCommand(email, new BigDecimal("10.50")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Customer email must not be null or empty");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "not-an-email",
                    "missing-at-sign.com",
                    "@example.com",
                    "user@",
                    "user@@example.com",
                    "user@example",
                    "user name@example.com",
                    "user@exam ple.com",
                    "user@.com",
                    "user@example.",
                    ".user@example.com",
                    "user.@example.com"
                })
        @DisplayName("Should throw when email format is invalid")
        void shouldThrowWhenEmailFormatIsInvalid(String invalidEmail) {
            assertThatThrownBy(() -> new PlaceOrderCommand(invalidEmail, new BigDecimal("10.50")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Customer email must be a valid email address");
        }
    }

    @Nested
    @DisplayName("Total invariant violations")
    class TotalInvariantViolations {

        @Test
        @DisplayName("Should throw when total is null")
        void shouldThrowWhenTotalIsNull() {
            assertThatThrownBy(() -> new PlaceOrderCommand("test@example.com", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Total must be >= 0");
        }

        @ParameterizedTest
        @ValueSource(strings = {"-0.01", "-1", "-10.50", "-999.99"})
        @DisplayName("Should throw when total is negative")
        void shouldThrowWhenTotalIsNegative(String negativeAmount) {
            assertThatThrownBy(() -> new PlaceOrderCommand("test@example.com", new BigDecimal(negativeAmount)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Total must be >= 0");
        }
    }

    @Nested
    @DisplayName("Combined invariant violations")
    class CombinedInvariantViolations {

        @Test
        @DisplayName("Should fail fast on first invariant violation (email checked before total)")
        void shouldFailFastOnFirstInvariant() {
            // Both email and total are invalid, but email is checked first
            assertThatThrownBy(() -> new PlaceOrderCommand(null, new BigDecimal("-1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Customer email must not be null or empty");
        }

        @Test
        @DisplayName("Should fail on email format when total is also invalid")
        void shouldFailOnEmailFormatWhenTotalIsInvalid() {
            // Invalid email format and negative total
            assertThatThrownBy(() -> new PlaceOrderCommand("not-an-email", new BigDecimal("-1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Customer email must be a valid email address");
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Should accept very small positive total")
        void shouldAcceptVerySmallPositiveTotal() {
            var cmd = new PlaceOrderCommand("test@example.com", new BigDecimal("0.01"));

            assertThat(cmd.total()).isEqualByComparingTo("0.01");
        }

        @Test
        @DisplayName("Should accept very large total")
        void shouldAcceptVeryLargeTotal() {
            var cmd = new PlaceOrderCommand("test@example.com", new BigDecimal("999999999.99"));

            assertThat(cmd.total()).isEqualByComparingTo("999999999.99");
        }

        @Test
        @DisplayName("Should accept email with maximum valid length components")
        void shouldAcceptEmailWithLongComponents() {
            // Valid email with long local and domain parts
            String longEmail = "a.very.long.email.address.with.many.dots@sub.domain.example.com";
            var cmd = new PlaceOrderCommand(longEmail, BigDecimal.TEN);

            assertThat(cmd.customerEmail()).isEqualTo(longEmail);
        }
    }
}

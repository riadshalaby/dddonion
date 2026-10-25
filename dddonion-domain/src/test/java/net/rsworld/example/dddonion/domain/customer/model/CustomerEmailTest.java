package net.rsworld.example.dddonion.domain.customer.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CustomerEmail invariants")
class CustomerEmailTest {

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
    @DisplayName("Accepts valid e-mail formats")
    void acceptsValidFormats(String email) {
        assertThat(new CustomerEmail(email).value()).isEqualTo(email);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Rejects null, empty or blank")
    void rejectsBlank(String email) {
        assertThatThrownBy(() -> new CustomerEmail(email))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null or empty");
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
                "a@b",
                "user name@example.com",
                "user@.com",
                "user@example.",
                ".user@example.com",
                "user.@example.com"
            })
    @DisplayName("Rejects invalid e-mail formats")
    void rejectsInvalidFormats(String email) {
        assertThatThrownBy(() -> new CustomerEmail(email))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be a valid email address");
    }

    @Test
    @DisplayName("Rejects e-mails longer than the maximum length")
    void rejectsTooLong() {
        String local = "a".repeat(CustomerEmail.MAX_LENGTH); // pushes total length past MAX_LENGTH
        assertThatThrownBy(() -> new CustomerEmail(local + "@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed");
    }
}

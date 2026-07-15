package net.rsworld.example.dddonion.domain.order.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("OrderId invariants")
class OrderIdTest {

    @Test
    @DisplayName("newId() produces a valid canonical UUID")
    void newIdIsValid() {
        OrderId id = OrderId.newId();
        assertThat(id.value()).matches(OrderId.PATTERN.pattern());
    }

    @ParameterizedTest
    @ValueSource(strings = {"3f6a9c1e-4b2d-4f0a-9c7e-8a1b2c3d4e5f", "3F6A9C1E-4B2D-4F0A-9C7E-8A1B2C3D4E5F"})
    @DisplayName("Accepts canonical UUIDs (case-insensitive)")
    void acceptsCanonicalUuids(String value) {
        assertThat(new OrderId(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("Rejects null, empty or blank")
    void rejectsBlank(String value) {
        assertThatThrownBy(() -> new OrderId(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null or blank");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "ORD-123",
                "not-a-uuid",
                "3f6a9c1e4b2d4f0a9c7e8a1b2c3d4e5f", // 32 chars, no dashes
                "3f6a9c1e-4b2d-4f0a-9c7e-8a1b2c3d4e5", // 35 chars
                "3f6a9c1e-4b2d-4f0a-9c7e-8a1b2c3d4e5ff", // 37 chars
                "z3f6a9c1-4b2d-4f0a-9c7e-8a1b2c3d4e5f" // non-hex character
            })
    @DisplayName("Rejects anything that is not a canonical UUID")
    void rejectsNonUuid(String value) {
        assertThatThrownBy(() -> new OrderId(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical UUID");
    }

    @Test
    @DisplayName("UUID.fromString would be too lenient — OrderId is stricter")
    void stricterThanUuidFromString() {
        // UUID.fromString accepts this malformed value; OrderId must not.
        UUID.fromString("1-1-1-1-1");
        assertThatThrownBy(() -> new OrderId("1-1-1-1-1")).isInstanceOf(IllegalArgumentException.class);
    }
}

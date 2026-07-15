package net.rsworld.example.dddonion.domain.order.model;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Identity of an {@link Order}. Self-validating value object: an {@code OrderId} can only ever exist in canonical UUID
 * form, so this rule lives in exactly one place. The same format is mirrored in the OpenAPI contract (schema
 * {@code OrderId}); the value-based drift test guards that both stay in sync.
 */
public record OrderId(String value) {

    /**
     * Canonical UUID (8-4-4-4-12 hex groups), case-insensitive. Kept intentionally strict: {@link UUID#fromString} is
     * too lenient and would accept malformed inputs such as {@code "1-1-1-1-1"}.
     */
    public static final Pattern PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    public OrderId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OrderId must not be null or blank");
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("OrderId must be a canonical UUID: " + value);
        }
    }

    public static OrderId newId() {
        return new OrderId(UUID.randomUUID().toString());
    }
}

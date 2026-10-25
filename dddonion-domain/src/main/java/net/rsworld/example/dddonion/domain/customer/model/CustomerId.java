package net.rsworld.example.dddonion.domain.customer.model;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Identity of a {@link Customer}. Self-validating value object: a {@code CustomerId} can only ever exist in canonical
 * UUID form, so this rule lives in exactly one place. The same format is mirrored in the OpenAPI contract (schema
 * {@code CustomerId}); the value-based drift test guards that both stay in sync.
 */
public record CustomerId(String value) {

    /**
     * Canonical UUID (8-4-4-4-12 hex groups), case-insensitive. Kept intentionally strict: {@link UUID#fromString} is
     * too lenient and would accept malformed inputs such as {@code "1-1-1-1-1"}.
     */
    public static final Pattern PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    public CustomerId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CustomerId must not be null or blank");
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("CustomerId must be a canonical UUID: " + value);
        }
    }

    public static CustomerId newId() {
        return new CustomerId(UUID.randomUUID().toString());
    }
}

package net.rsworld.example.dddonion.domain.order.model;

import java.util.regex.Pattern;

/**
 * Customer's e-mail address as a self-validating value object. This is the single source of truth for e-mail validity
 * inside the domain; the OpenAPI schema {@code CustomerEmail} mirrors the same {@code maxLength}/{@code pattern} for
 * boundary validation, guarded by the value-based drift test.
 */
public record CustomerEmail(String value) {

    /** Upper bound per RFC 5321; also mirrored as {@code maxLength} in the OpenAPI contract. */
    public static final int MAX_LENGTH = 254;

    /** Strict pattern: no leading/trailing dots in the local part, requires a real TLD. */
    public static final Pattern PATTERN = Pattern.compile(
            "^[A-Za-z0-9][A-Za-z0-9+._-]*[A-Za-z0-9]@[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*\\.[A-Za-z]{2,}$|^[A-Za-z0-9]@[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*\\.[A-Za-z]{2,}$");

    public CustomerEmail {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Customer email must not be null or empty");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Customer email must not exceed " + MAX_LENGTH + " characters");
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Customer email must be a valid email address");
        }
    }
}

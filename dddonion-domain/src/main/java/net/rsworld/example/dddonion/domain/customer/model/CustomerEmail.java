package net.rsworld.example.dddonion.domain.customer.model;

import java.util.regex.Pattern;

/**
 * E-Mail-Adresse eines Customers als selbstvalidierendes Value Object. Innerhalb der Domain ist sie die einzige Quelle
 * der Wahrheit für gültige E-Mails; das OpenAPI-Schema {@code CustomerEmail} spiegelt dieselben
 * {@code maxLength}/{@code pattern} für die Validierung am Rand, abgesichert durch den wertbasierten Drift-Test.
 */
public record CustomerEmail(String value) {

    /** Obergrenze nach RFC 5321; im OpenAPI-Vertrag ebenfalls als {@code maxLength} gespiegelt. */
    public static final int MAX_LENGTH = 254;

    /** Strenges Pattern: keine Punkte am Anfang oder Ende des lokalen Teils, verlangt eine echte TLD. */
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

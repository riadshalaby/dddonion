package net.rsworld.example.dddonion.domain.order.model;

import java.math.BigDecimal;

/**
 * A non-negative monetary amount (used as an order total) as a self-validating value object. Mirrors the OpenAPI schema
 * {@code MonetaryAmount} ({@code minimum: 0}); the value-based drift test guards that both agree.
 */
public record Money(BigDecimal amount) {

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("Amount must not be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must be >= 0");
        }
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }
}

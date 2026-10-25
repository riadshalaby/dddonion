package net.rsworld.example.dddonion.domain.common;

import java.math.BigDecimal;

/**
 * A non-negative monetary amount as a self-validating value object. Mirrors the OpenAPI schema {@code MonetaryAmount}
 * ({@code minimum: 0}); the value-based drift test guards that both agree.
 *
 * <p>Equality is by value, ignoring scale: {@code 100} equals {@code 100.00}. The record's generated
 * {@code equals}/{@code hashCode} would use {@link BigDecimal#equals}, which is scale-sensitive, so an amount read back
 * from a {@code DECIMAL(19,2)} column would no longer equal the amount that was saved. The amount itself is kept as
 * given.
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

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    /** Compares by value, ignoring scale ({@code 100.00} is not greater than {@code 100}). */
    public boolean isGreaterThan(Money other) {
        return amount.compareTo(other.amount) > 0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Money other && amount.compareTo(other.amount) == 0;
    }

    @Override
    public int hashCode() {
        return amount.stripTrailingZeros().hashCode();
    }
}

package net.rsworld.example.dddonion.domain.common;

import java.math.BigDecimal;

/**
 * Ein nicht negativer Geldbetrag als selbstvalidierendes Value Object. Spiegelt das OpenAPI-Schema
 * {@code MonetaryAmount} ({@code minimum: 0}); der wertbasierte Drift-Test stellt sicher, dass beide übereinstimmen.
 *
 * <p>Gleichheit gilt nach Wert, unabhängig von der Skalierung: {@code 100} ist gleich {@code 100.00}. Das vom Record
 * generierte {@code equals}/{@code hashCode} würde {@link BigDecimal#equals} nutzen, das die Skalierung berücksichtigt;
 * ein aus einer {@code DECIMAL(19,2)}-Spalte gelesener Betrag wäre dann nicht mehr gleich dem gespeicherten. Der Betrag
 * selbst bleibt so, wie er übergeben wurde.
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

    /** Vergleicht nach Wert, unabhängig von der Skalierung ({@code 100.00} ist nicht größer als {@code 100}). */
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

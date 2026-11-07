package net.rsworld.example.dddonion.domain.customer.model;

import java.util.Objects;
import net.rsworld.example.dddonion.domain.common.Money;

/**
 * Aggregate Root mit dem Kreditlimit eines Customers und dem aktuell offenen Betrag. Eine Order ist erlaubt, solange
 * {@code openAmount + total <= creditLimit} gilt (Gleichheit ist erlaubt). Die Version ist nur ein Concurrency-Token
 * für die Persistenz; Domain-Methoden ändern sie nie.
 */
public class Customer {

    private final CustomerId id;
    private final CustomerEmail email;
    private Money creditLimit;
    private Money openAmount;
    private final long version;

    /** Legt einen neuen Customer ohne offenen Betrag an. */
    public Customer(CustomerEmail email, Money creditLimit) {
        this(CustomerId.newId(), email, creditLimit, Money.zero(), 0L);
    }

    /** Stellt einen gespeicherten Customer wieder her. */
    public Customer(CustomerId id, CustomerEmail email, Money creditLimit, Money openAmount, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email");
        this.creditLimit = Objects.requireNonNull(creditLimit, "creditLimit");
        this.openAmount = Objects.requireNonNull(openAmount, "openAmount");
        this.version = version;
    }

    /**
     * Reserviert Kredit für eine Order: scheitert, wenn der offene Betrag das Kreditlimit überschreiten würde, und
     * addiert sonst den Betrag zum offenen Betrag.
     */
    public void reserveCredit(Money orderTotal) {
        Money newOpenAmount = openAmount.plus(orderTotal);
        if (newOpenAmount.isGreaterThan(creditLimit)) {
            throw new CreditLimitExceededException(id);
        }
        this.openAmount = newOpenAmount;
    }

    /** Das neue Limit darf unter dem offenen Betrag liegen; dann sperrt es lediglich weitere Orders. */
    public void changeCreditLimit(Money newLimit) {
        this.creditLimit = Objects.requireNonNull(newLimit, "newLimit");
    }

    public CustomerId id() {
        return id;
    }

    public CustomerEmail email() {
        return email;
    }

    public Money creditLimit() {
        return creditLimit;
    }

    public Money openAmount() {
        return openAmount;
    }

    public long version() {
        return version;
    }
}

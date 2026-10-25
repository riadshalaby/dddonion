package net.rsworld.example.dddonion.domain.customer.model;

import java.util.Objects;
import net.rsworld.example.dddonion.domain.common.Money;

/**
 * Aggregate root holding a customer's credit limit and the amount currently open. An order is allowed as long as
 * {@code openAmount + total <= creditLimit} (equality is allowed). The version is only a concurrency token for
 * persistence; domain methods never change it.
 */
public class Customer {

    private final CustomerId id;
    private final CustomerEmail email;
    private Money creditLimit;
    private Money openAmount;
    private final long version;

    /** Creates a new customer without open amount. */
    public Customer(CustomerEmail email, Money creditLimit) {
        this(CustomerId.newId(), email, creditLimit, Money.zero(), 0L);
    }

    /** Reconstitutes a persisted customer. */
    public Customer(CustomerId id, CustomerEmail email, Money creditLimit, Money openAmount, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email");
        this.creditLimit = Objects.requireNonNull(creditLimit, "creditLimit");
        this.openAmount = Objects.requireNonNull(openAmount, "openAmount");
        this.version = version;
    }

    /** Fails if accepting an order of the given total would push the open amount above the credit limit. */
    public void ensureCreditFor(Money orderTotal) {
        if (openAmount.plus(orderTotal).isGreaterThan(creditLimit)) {
            throw new CreditLimitExceededException(id);
        }
    }

    public void recordOrder(Money amount) {
        this.openAmount = openAmount.plus(amount);
    }

    /** The new limit may be below the open amount; it then merely blocks further orders. */
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

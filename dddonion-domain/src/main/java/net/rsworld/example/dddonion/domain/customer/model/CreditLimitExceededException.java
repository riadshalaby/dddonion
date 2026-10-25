package net.rsworld.example.dddonion.domain.customer.model;

public class CreditLimitExceededException extends RuntimeException {

    public CreditLimitExceededException(CustomerId customerId) {
        super("Credit limit exceeded for customer " + customerId.value());
    }
}

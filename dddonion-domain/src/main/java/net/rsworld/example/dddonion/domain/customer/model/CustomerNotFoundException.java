package net.rsworld.example.dddonion.domain.customer.model;

public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(CustomerId customerId) {
        super("Customer not found: " + customerId.value());
    }
}

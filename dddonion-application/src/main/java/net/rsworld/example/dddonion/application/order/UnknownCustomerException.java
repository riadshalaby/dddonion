package net.rsworld.example.dddonion.application.order;

import net.rsworld.example.dddonion.domain.customer.model.CustomerId;

/**
 * Raised when an order refers to a customer that does not exist. Lives outside {@code usecase}: that package is
 * reserved for {@code @UseCase} interfaces.
 */
public class UnknownCustomerException extends RuntimeException {

    public UnknownCustomerException(CustomerId id) {
        super("Unknown customer " + id.value());
    }
}

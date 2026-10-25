package net.rsworld.example.dddonion.domain.customer.model;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(CustomerEmail email) {
        super("E-mail already registered: " + email.value());
    }
}

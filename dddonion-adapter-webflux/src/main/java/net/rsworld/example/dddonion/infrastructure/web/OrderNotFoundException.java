package net.rsworld.example.dddonion.infrastructure.web;

/** Raised by the web adapter when a requested order does not exist; mapped to 404 problem+json. */
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String orderId) {
        super("No order found with id " + orderId);
    }
}

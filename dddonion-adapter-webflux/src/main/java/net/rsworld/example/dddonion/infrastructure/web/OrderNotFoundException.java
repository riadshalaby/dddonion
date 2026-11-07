package net.rsworld.example.dddonion.infrastructure.web;

/** Wirft der Web-Adapter, wenn eine angefragte Order nicht existiert; wird auf 404 problem+json abgebildet. */
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String orderId) {
        super("No order found with id " + orderId);
    }
}

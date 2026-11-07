package net.rsworld.example.dddonion.application.order;

import net.rsworld.example.dddonion.domain.customer.model.CustomerId;

/**
 * Wird geworfen, wenn eine Order auf einen Customer verweist, den es nicht gibt. Liegt außerhalb von {@code usecase}:
 * Dieses Paket ist für {@code @UseCase}-Interfaces reserviert.
 */
public class UnknownCustomerException extends RuntimeException {

    public UnknownCustomerException(CustomerId id) {
        super("Unknown customer " + id.value());
    }
}

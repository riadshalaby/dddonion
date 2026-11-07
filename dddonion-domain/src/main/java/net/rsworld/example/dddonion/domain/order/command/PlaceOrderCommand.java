package net.rsworld.example.dddonion.domain.order.command;

import java.util.Objects;
import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;

/**
 * Command zum Anlegen einer Order, zusammengesetzt aus bereits validierten Value Objects. Da {@link CustomerId} und
 * {@link Money} ihre Format- und Wertebereichsregeln selbst durchsetzen, muss der Command nur ihr Vorhandensein prüfen
 * — die eigentliche Validierung wird hier nicht dupliziert.
 */
public record PlaceOrderCommand(CustomerId customerId, Money total) {

    public PlaceOrderCommand {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(total, "total must not be null");
    }
}

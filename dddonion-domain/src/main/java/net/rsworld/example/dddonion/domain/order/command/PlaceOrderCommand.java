package net.rsworld.example.dddonion.domain.order.command;

import java.util.Objects;
import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;

/**
 * Command to place an order, composed of already-validated value objects. Because {@link CustomerId} and {@link Money}
 * enforce their own format/range rules, the command only has to assert their presence — the actual validation is not
 * duplicated here.
 */
public record PlaceOrderCommand(CustomerId customerId, Money total) {

    public PlaceOrderCommand {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(total, "total must not be null");
    }
}

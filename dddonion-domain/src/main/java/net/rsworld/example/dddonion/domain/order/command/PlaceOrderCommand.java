package net.rsworld.example.dddonion.domain.order.command;

import java.util.Objects;
import net.rsworld.example.dddonion.domain.order.model.CustomerEmail;
import net.rsworld.example.dddonion.domain.order.model.Money;

/**
 * Command to place an order, composed of already-validated value objects. Because {@link CustomerEmail} and
 * {@link Money} enforce their own format/range rules, the command only has to assert their presence — the actual
 * validation is not duplicated here.
 */
public record PlaceOrderCommand(CustomerEmail customerEmail, Money total) {

    public PlaceOrderCommand {
        Objects.requireNonNull(customerEmail, "customerEmail must not be null");
        Objects.requireNonNull(total, "total must not be null");
    }
}

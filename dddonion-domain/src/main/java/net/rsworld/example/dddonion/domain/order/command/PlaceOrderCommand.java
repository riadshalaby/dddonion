package net.rsworld.example.dddonion.domain.order.command;

import java.math.BigDecimal;
import java.util.regex.Pattern;

public record PlaceOrderCommand(String customerEmail, BigDecimal total) {

    // More strict pattern: no leading/trailing dots in local part, no consecutive dots
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9][A-Za-z0-9+._-]*[A-Za-z0-9]@[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*\\.[A-Za-z]{2,}$|^[A-Za-z0-9]@[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*\\.[A-Za-z]{2,}$");

    public PlaceOrderCommand {
        if (customerEmail == null || customerEmail.isBlank()) {
            throw new IllegalArgumentException("Customer email must not be null or empty");
        }
        if (!EMAIL_PATTERN.matcher(customerEmail).matches()) {
            throw new IllegalArgumentException("Customer email must be a valid email address");
        }
        if (total == null || total.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Total must be >= 0");
        }
    }
}

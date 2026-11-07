package net.rsworld.example.dddonion.domain.order.model;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Identität einer {@link Order}. Selbstvalidierendes Value Object: Eine {@code OrderId} existiert nur in kanonischer
 * UUID-Form, daher steht diese Regel an genau einer Stelle. Dasselbe Format spiegelt der OpenAPI-Vertrag (Schema
 * {@code OrderId}); der wertbasierte Drift-Test stellt sicher, dass beide synchron bleiben.
 */
public record OrderId(String value) {

    /**
     * Kanonische UUID (Hex-Gruppen 8-4-4-4-12), Groß-/Kleinschreibung egal. Bewusst streng: {@link UUID#fromString} ist
     * zu nachsichtig und würde fehlerhafte Eingaben wie {@code "1-1-1-1-1"} akzeptieren.
     */
    public static final Pattern PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    public OrderId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OrderId must not be null or blank");
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("OrderId must be a canonical UUID: " + value);
        }
    }

    public static OrderId newId() {
        return new OrderId(UUID.randomUUID().toString());
    }
}

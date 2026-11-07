package net.rsworld.example.dddonion.architecture;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Markiert einen Use-Case-Port der Application-Schicht und beschreibt ihn für Wiring, Metriken und Audit. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UseCase {

    /**
     * Stabiler Name des Use Cases; Konvention {@code <aggregate>.<verb>}, z. B. {@code order.place}. Weitere durch
     * Punkte getrennte, kleingeschriebene Segmente und Bindestriche sind erlaubt (Pattern
     * {@code ^[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*)+$}), z. B. {@code customer.change-credit-limit}.
     *
     * <p>Der Name ist Teil des Metrik- und Audit-Vertrags (Label {@code usecase}). Er darf sich beim Umbenennen des
     * Interfaces nicht ändern.
     */
    String name();

    /**
     * Semantik als Command oder Query, nicht der Auslöser. Ein durch ein Event ausgelöster Use Case, der Zustand
     * ändert, ist ein {@link Kind#COMMAND}; der Auslöser zeigt sich im {@code channel} der Metadaten.
     */
    Kind kind();

    enum Kind {
        COMMAND,
        QUERY
    }
}

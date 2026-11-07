package net.rsworld.example.dddonion.application.context;

/**
 * Fachliche Einordnung dessen, der einen Use Case auslöst. Bewusst grob (geringe Kardinalität), damit sie als
 * Metrik-Dimension taugt; sie bezeichnet eine Art von Aufrufer, nie einen einzelnen Benutzer.
 */
public enum ActorType {
    PRIVATE_CUSTOMER,
    BUSINESS_CUSTOMER,
    EXTERNAL_ERP,
    /** Der eingehende Adapter konnte den Aufrufer nicht einordnen. */
    UNKNOWN
}

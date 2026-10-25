package net.rsworld.example.dddonion.application.context;

/**
 * Business classification of whoever triggers a use case. Deliberately coarse (low cardinality) so it can be used as a
 * metric dimension; it identifies a kind of caller, never an individual user.
 */
public enum ActorType {
    PRIVATE_CUSTOMER,
    BUSINESS_CUSTOMER,
    EXTERNAL_ERP,
    /** The inbound adapter could not classify the caller. */
    UNKNOWN
}

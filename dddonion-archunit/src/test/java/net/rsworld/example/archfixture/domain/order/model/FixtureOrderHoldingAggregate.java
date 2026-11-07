package net.rsworld.example.archfixture.domain.order.model;

import net.rsworld.example.archfixture.domain.customer.model.FixtureCustomer;

/** Fixture: Verstoß, hält ein fremdes Aggregate statt seiner Id. */
public class FixtureOrderHoldingAggregate {
    FixtureCustomer customer;
}

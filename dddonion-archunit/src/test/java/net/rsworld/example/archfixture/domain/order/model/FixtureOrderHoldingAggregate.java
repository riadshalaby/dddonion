package net.rsworld.example.archfixture.domain.order.model;

import net.rsworld.example.archfixture.domain.customer.model.FixtureCustomer;

/** Fixture: violation, holds a foreign aggregate instead of its id. */
public class FixtureOrderHoldingAggregate {
    FixtureCustomer customer;
}

package net.rsworld.example.dddonion.domain.customer.command;

import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.model.CustomerEmail;

public record RegisterCustomerCommand(CustomerEmail email, Money creditLimit) {}

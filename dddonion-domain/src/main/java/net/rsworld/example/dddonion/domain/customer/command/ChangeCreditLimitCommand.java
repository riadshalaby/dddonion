package net.rsworld.example.dddonion.domain.customer.command;

import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;

public record ChangeCreditLimitCommand(CustomerId customerId, Money creditLimit) {}

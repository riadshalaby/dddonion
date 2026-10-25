package net.rsworld.example.dddonion.domain.customer.command;

import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.domain.order.model.OrderId;

public record RecordOrderExposureCommand(CustomerId customerId, OrderId orderId, Money amount) {}

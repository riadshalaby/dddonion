package net.rsworld.example.dddonion.infrastructure.web;

import java.math.BigDecimal;
import net.rsworld.example.dddonion.application.order.usecase.PlaceOrderUseCase;
import net.rsworld.example.dddonion.domain.order.command.PlaceOrderCommand;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final PlaceOrderUseCase placeOrder;

    public OrderController(PlaceOrderUseCase placeOrder) {
        this.placeOrder = placeOrder;
    }

    @PostMapping(produces = "text/plain")
    public Mono<String> create(@RequestParam String email, @RequestParam BigDecimal total) {
        return placeOrder.handle(new PlaceOrderCommand(email, total)).map(OrderId::value);
    }
}

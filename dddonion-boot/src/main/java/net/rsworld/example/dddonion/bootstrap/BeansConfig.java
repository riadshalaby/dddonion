package net.rsworld.example.dddonion.bootstrap;

import net.rsworld.example.dddonion.application.order.service.PlaceOrderService;
import net.rsworld.example.dddonion.application.order.usecase.PlaceOrderUseCase;
import net.rsworld.example.dddonion.application.outbox.OutboxPort;
import net.rsworld.example.dddonion.domain.order.repository.OrderRepository;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.adapter.OrderRepositoryAdapter;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.repo.OrderR2dbcRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@Configuration
@EnableR2dbcRepositories(basePackages = "net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.repo")
public class BeansConfig {

    @Bean
    public OrderRepository orderRepository(OrderR2dbcRepository r2dbcRepo) {
        return new OrderRepositoryAdapter(r2dbcRepo);
    }

    @Bean
    public PlaceOrderUseCase placeOrderUseCase(
            OrderRepository orders, OutboxPort outbox, ApplicationEventPublisher applicationEventPublisher) {
        return new PlaceOrderService(orders, outbox, applicationEventPublisher);
    }
}

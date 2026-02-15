package net.rsworld.example.dddonion.bootstrap;

import net.rsworld.example.dddonion.application.event.DomainEventPublisherPort;
import net.rsworld.example.dddonion.application.order.service.PlaceOrderService;
import net.rsworld.example.dddonion.application.order.usecase.PlaceOrderUseCase;
import net.rsworld.example.dddonion.domain.order.repository.OrderRepository;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.adapter.OrderRepositoryAdapter;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.repo.OrderR2dbcRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import reactor.core.publisher.Mono;

@Configuration
@EnableR2dbcRepositories(basePackages = "net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.repo")
public class BeansConfig {

    @Bean
    public OrderRepository orderRepository(OrderR2dbcRepository r2dbcRepo) {
        return new OrderRepositoryAdapter(r2dbcRepo);
    }

    @Bean
    public DomainEventPublisherPort domainEventPublisherPort(ApplicationEventPublisher applicationEventPublisher) {
        return event -> Mono.fromRunnable(() -> applicationEventPublisher.publishEvent(event));
    }

    @Bean
    public PlaceOrderUseCase placeOrderUseCase(OrderRepository orders, DomainEventPublisherPort eventPublisher) {
        return new PlaceOrderService(orders, eventPublisher);
    }
}

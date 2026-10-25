package net.rsworld.example.dddonion.bootstrap;

import net.rsworld.example.dddonion.application.event.DomainEventPublisherPort;
import net.rsworld.example.dddonion.domain.customer.repository.CustomerRepository;
import net.rsworld.example.dddonion.domain.order.repository.OrderRepository;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.adapter.CustomerRepositoryAdapter;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.adapter.OrderRepositoryAdapter;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.repo.CustomerR2dbcRepository;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.repo.OrderR2dbcRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import reactor.core.publisher.Mono;

@Configuration
@EnableR2dbcRepositories(basePackages = "net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.repo")
public class BeansConfig {

    @Bean
    public OrderRepository orderRepository(OrderR2dbcRepository r2dbcRepo, R2dbcEntityTemplate template) {
        return new OrderRepositoryAdapter(r2dbcRepo, template);
    }

    @Bean
    public CustomerRepository customerRepository(CustomerR2dbcRepository r2dbcRepo) {
        return new CustomerRepositoryAdapter(r2dbcRepo);
    }

    @Bean
    public DomainEventPublisherPort domainEventPublisherPort(ApplicationEventPublisher applicationEventPublisher) {
        return event -> Mono.fromRunnable(() -> applicationEventPublisher.publishEvent(event));
    }
}

package net.rsworld.example.dddonion.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.micrometer.observation.ObservationRegistry;
import net.rsworld.example.dddonion.application.customer.usecase.ChangeCreditLimitUseCase;
import net.rsworld.example.dddonion.application.customer.usecase.GetCustomerUseCase;
import net.rsworld.example.dddonion.application.customer.usecase.RegisterCustomerUseCase;
import net.rsworld.example.dddonion.application.event.DomainEventPublisherPort;
import net.rsworld.example.dddonion.application.order.usecase.PlaceOrderUseCase;
import net.rsworld.example.dddonion.architecture.UseCase;
import net.rsworld.example.dddonion.bootstrap.observability.ObservabilityConfig;
import net.rsworld.example.dddonion.bootstrap.observability.UseCaseObservability;
import net.rsworld.example.dddonion.domain.customer.repository.CustomerRepository;
import net.rsworld.example.dddonion.domain.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.ClassUtils;

/** Guards against a use case bean escaping the observability decorator (and thus the audit). */
class UseCaseWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ApplicationWiringConfig.class, ObservabilityConfig.class)
            .withBean(ObservationRegistry.class, ObservationRegistry::create)
            .withBean(OrderRepository.class, () -> mock(OrderRepository.class))
            .withBean(CustomerRepository.class, () -> mock(CustomerRepository.class))
            .withBean(DomainEventPublisherPort.class, () -> mock(DomainEventPublisherPort.class));

    @Test
    @DisplayName("Every bean implementing a @UseCase interface is observed")
    void everyUseCaseBeanIsObserved() {
        runner.run(context -> {
            var useCaseBeans = context.getBeansOfType(Object.class).values().stream()
                    .filter(bean -> ClassUtils.getAllInterfacesForClassAsSet(bean.getClass()).stream()
                            .anyMatch(i -> i.isAnnotationPresent(UseCase.class)))
                    .toList();

            assertThat(useCaseBeans).hasSizeGreaterThanOrEqualTo(5);
            assertThat(useCaseBeans).allMatch(UseCaseObservability::isObserved);
        });
    }

    @Test
    void placeOrderIsWiredUnderItsName() {
        runner.run(context ->
                assertThat(context.getBean(PlaceOrderUseCase.class).toString()).startsWith("Observed[order.place]"));
    }

    @Test
    void customerUseCasesAreWiredUnderTheirNames() {
        runner.run(context -> {
            assertThat(context.getBean(RegisterCustomerUseCase.class).toString())
                    .startsWith("Observed[customer.register]");
            assertThat(context.getBean(GetCustomerUseCase.class).toString()).startsWith("Observed[customer.get]");
            assertThat(context.getBean(ChangeCreditLimitUseCase.class).toString())
                    .startsWith("Observed[customer.change-credit-limit]");
        });
    }
}

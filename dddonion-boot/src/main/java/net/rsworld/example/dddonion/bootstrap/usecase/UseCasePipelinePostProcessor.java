package net.rsworld.example.dddonion.bootstrap.usecase;

import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import net.rsworld.example.dddonion.architecture.TransactionalUseCase;
import net.rsworld.example.dddonion.architecture.UseCase;
import net.rsworld.example.dddonion.bootstrap.observability.UseCaseObservability;
import net.rsworld.example.dddonion.bootstrap.transaction.UseCaseTransaction;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.ClassUtils;
import reactor.core.publisher.Mono;

/**
 * Builds the use case pipeline around every bean that implements a {@link UseCase} interface, so a new use case cannot
 * reach the application without audit and metrics. From the outside in: {@link UseCaseObservability}, then, for a bean
 * annotated with {@link TransactionalUseCase}, {@link UseCaseTransaction} (retry, transaction), then the service.
 *
 * <p>Fails the startup if a bean implements more than one use case interface, if two beans share a use case name, if no
 * {@link ObservationRegistry} is available (a use case must never run unaudited, so this is deliberate), or if
 * {@link TransactionalUseCase} sits on a class that is not a {@link UseCase.Kind#COMMAND} use case.
 *
 * <p>Runs last among the {@link Ordered} post-processors, so the observation is the outermost layer of those with lower
 * precedence. Application services carry no Spring advice (the application layer is Spring-free), hence no proxy is
 * stacked around the observed use case.
 */
public class UseCasePipelinePostProcessor implements BeanPostProcessor, Ordered {

    private final ObjectProvider<ObservationRegistry> registry;
    private final ObjectProvider<TransactionalOperator> tx;
    private final Map<String, Registration> namesInUse = new ConcurrentHashMap<>();

    public UseCasePipelinePostProcessor(
            ObjectProvider<ObservationRegistry> registry, ObjectProvider<TransactionalOperator> tx) {
        this.registry = registry;
        this.tx = tx;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (UseCaseObservability.isObserved(bean)) {
            return bean;
        }
        boolean transactional = AnnotatedElementUtils.hasAnnotation(bean.getClass(), TransactionalUseCase.class);
        List<Class<?>> useCases = ClassUtils.getAllInterfacesForClassAsSet(bean.getClass()).stream()
                .filter(i -> i.isAnnotationPresent(UseCase.class))
                .toList();
        if (useCases.isEmpty()) {
            if (transactional) {
                throw new IllegalStateException("@TransactionalUseCase on "
                        + bean.getClass().getName() + " requires the class to implement a @UseCase interface");
            }
            return bean;
        }
        if (useCases.size() > 1) {
            throw new IllegalStateException(
                    "Bean '" + beanName + "' implements more than one @UseCase interface: " + useCases);
        }
        Class<?> useCase = useCases.get(0);
        if (transactional && useCase.getAnnotation(UseCase.class).kind() != UseCase.Kind.COMMAND) {
            throw new IllegalStateException(
                    "@TransactionalUseCase on " + bean.getClass().getName()
                            + " is only allowed for a use case of kind COMMAND, but " + useCase.getName() + " is "
                            + useCase.getAnnotation(UseCase.class).kind());
        }
        String name = useCase.getAnnotation(UseCase.class).name();
        Registration previous = namesInUse.putIfAbsent(name, new Registration(beanName, useCase));
        if (previous != null) {
            throw new IllegalStateException("Duplicate @UseCase name '" + name + "': bean '" + previous.beanName()
                    + "' (" + previous.useCase().getName() + ") and bean '" + beanName + "' (" + useCase.getName()
                    + ")");
        }
        return wrap(useCase, bean, transactional);
    }

    @SuppressWarnings("unchecked")
    private <T> T wrap(Class<T> useCase, Object bean, boolean transactional) {
        UnaryOperator<Mono<?>> inner =
                transactional ? new UseCaseTransaction(tx.getObject()) : UnaryOperator.identity();
        return UseCaseObservability.wrap(useCase, (T) bean, registry.getObject(), inner);
    }

    @Override
    public int getOrder() {
        // last among Ordered post-processors: wraps the finished bean; see class Javadoc
        return Ordered.LOWEST_PRECEDENCE;
    }

    private record Registration(String beanName, Class<?> useCase) {}
}

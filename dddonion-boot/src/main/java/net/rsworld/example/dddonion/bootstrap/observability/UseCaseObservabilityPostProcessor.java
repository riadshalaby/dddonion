package net.rsworld.example.dddonion.bootstrap.observability;

import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.rsworld.example.dddonion.architecture.UseCase;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.util.ClassUtils;

/**
 * Wraps every bean that implements a {@link UseCase} interface with {@link UseCaseObservability}, so a new use case
 * cannot reach the application without audit and metrics. Fails the startup if a bean implements more than one use case
 * interface, if two beans share a use case name, or if no {@link ObservationRegistry} is available (a use case must
 * never run unaudited, so this is deliberate).
 *
 * <p>Runs last among the {@link Ordered} post-processors, so the observation is the outermost layer of those with lower
 * precedence. Application services carry no Spring advice (the application layer is Spring-free), hence no proxy is
 * stacked around the observed use case.
 */
public class UseCaseObservabilityPostProcessor implements BeanPostProcessor, Ordered {

    private final ObjectProvider<ObservationRegistry> registry;
    private final Map<String, Registration> namesInUse = new ConcurrentHashMap<>();

    public UseCaseObservabilityPostProcessor(ObjectProvider<ObservationRegistry> registry) {
        this.registry = registry;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (UseCaseObservability.isObserved(bean)) {
            return bean;
        }
        List<Class<?>> useCases = ClassUtils.getAllInterfacesForClassAsSet(bean.getClass()).stream()
                .filter(i -> i.isAnnotationPresent(UseCase.class))
                .toList();
        if (useCases.isEmpty()) {
            return bean;
        }
        if (useCases.size() > 1) {
            throw new IllegalStateException(
                    "Bean '" + beanName + "' implements more than one @UseCase interface: " + useCases);
        }
        Class<?> useCase = useCases.get(0);
        String name = useCase.getAnnotation(UseCase.class).name();
        Registration previous = namesInUse.putIfAbsent(name, new Registration(beanName, useCase));
        if (previous != null) {
            throw new IllegalStateException("Duplicate @UseCase name '" + name + "': bean '" + previous.beanName()
                    + "' (" + previous.useCase().getName() + ") and bean '" + beanName + "' (" + useCase.getName()
                    + ")");
        }
        return wrap(useCase, bean);
    }

    @SuppressWarnings("unchecked")
    private <T> T wrap(Class<T> useCase, Object bean) {
        return UseCaseObservability.wrap(useCase, (T) bean, registry.getObject());
    }

    @Override
    public int getOrder() {
        // last among Ordered post-processors: wraps the finished bean; see class Javadoc
        return Ordered.LOWEST_PRECEDENCE;
    }

    private record Registration(String beanName, Class<?> useCase) {}
}

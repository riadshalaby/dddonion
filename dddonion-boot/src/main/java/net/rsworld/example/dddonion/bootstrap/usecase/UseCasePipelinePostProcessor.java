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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.ClassUtils;
import reactor.core.publisher.Mono;

/**
 * Baut die Use-Case-Pipeline um jedes Bean, das ein {@link UseCase}-Interface implementiert, damit kein neuer Use Case
 * ohne Audit und Metriken in die Anwendung gelangt. Von außen nach innen: {@link UseCaseObservability}, dann bei einem
 * mit {@link TransactionalUseCase} annotierten Bean {@link UseCaseTransaction} (Retry, Transaktion,
 * Post-Commit-Events), dann der Service.
 *
 * <p>Bricht den Start ab, wenn ein Bean mehr als ein Use-Case-Interface implementiert, wenn sich zwei Beans einen
 * Use-Case-Namen teilen, wenn keine {@link ObservationRegistry} verfügbar ist (ein Use Case darf nie ohne Audit laufen,
 * das ist Absicht) oder wenn {@link TransactionalUseCase} an einer Klasse steht, die kein
 * {@link UseCase.Kind#COMMAND}-Use-Case ist.
 *
 * <p>Läuft als letzter der {@link Ordered}-Post-Processors, daher ist die Observation die äußerste Schicht über denen
 * mit niedrigerer Priorität. Application Services tragen kein Spring-Advice (die Application-Schicht ist Spring-frei),
 * daher wird kein Proxy um den beobachteten Use Case gestapelt.
 */
public class UseCasePipelinePostProcessor implements BeanPostProcessor, Ordered {

    private final ObjectProvider<ObservationRegistry> registry;
    private final ObjectProvider<TransactionalOperator> tx;
    private final ObjectProvider<ApplicationEventPublisher> events;
    private final Map<String, Registration> namesInUse = new ConcurrentHashMap<>();

    public UseCasePipelinePostProcessor(
            ObjectProvider<ObservationRegistry> registry,
            ObjectProvider<TransactionalOperator> tx,
            ObjectProvider<ApplicationEventPublisher> events) {
        this.registry = registry;
        this.tx = tx;
        this.events = events;
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
                transactional ? new UseCaseTransaction(tx.getObject(), events.getObject()) : UnaryOperator.identity();
        return UseCaseObservability.wrap(useCase, (T) bean, registry.getObject(), inner);
    }

    @Override
    public int getOrder() {
        // letzter der Ordered-Post-Processors: hüllt das fertige Bean ein; siehe Klassen-Javadoc
        return Ordered.LOWEST_PRECEDENCE;
    }

    private record Registration(String beanName, Class<?> useCase) {}
}

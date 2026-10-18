package net.rsworld.example.dddonion.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.micrometer.observation.ObservationRegistry;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;

/** Guards against a new use case bean being wired without the observability decorator (and thus without audit). */
class BeansConfigUseCaseObservabilityTest {

    @Test
    @DisplayName("Every *UseCase bean in BeansConfig is wrapped by UseCaseObservability")
    void everyUseCaseBeanIsObserved() throws Exception {
        BeansConfig config = new BeansConfig();
        List<Method> useCaseBeans = Arrays.stream(BeansConfig.class.getMethods())
                .filter(m -> m.isAnnotationPresent(Bean.class))
                .filter(m -> m.getReturnType().getSimpleName().endsWith("UseCase"))
                .toList();

        assertThat(useCaseBeans).isNotEmpty();
        for (Method bean : useCaseBeans) {
            Object[] args = Arrays.stream(bean.getParameterTypes())
                    .map(type -> type == ObservationRegistry.class ? ObservationRegistry.NOOP : mock(type))
                    .toArray();
            Object useCase = bean.invoke(config, args);
            assertThat(Proxy.isProxyClass(useCase.getClass()))
                    .as("%s must be wrapped with UseCaseObservability.wrap(...)", bean.getName())
                    .isTrue();
            assertThat(useCase.toString()).startsWith("Observed[");
        }
    }
}

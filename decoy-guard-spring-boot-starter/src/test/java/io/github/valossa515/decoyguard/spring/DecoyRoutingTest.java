package io.github.valossa515.decoyguard.spring;

import io.github.valossa515.decoyguard.core.ActivationTrigger;
import io.github.valossa515.decoyguard.core.DuressContext;
import io.github.valossa515.decoyguard.core.DuressContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DecoyRoutingTest {

    interface Greeter {
        String greet();
    }

    static class RealGreeter implements Greeter {
        @Override
        public String greet() {
            return "real";
        }
    }

    @Decoy
    static class DecoyGreeter implements Greeter {
        @Override
        public String greet() {
            return "decoy";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Beans {
        @Bean
        Greeter realGreeter() {
            return new RealGreeter();
        }

        @Bean
        DecoyGreeter decoyGreeter() {
            return new DecoyGreeter();
        }
    }

    static class Consumer {
        final Greeter greeter;

        Consumer(Greeter greeter) {
            this.greeter = greeter;
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DecoyGuardAutoConfiguration.class))
            .withUserConfiguration(Beans.class)
            .withBean(Consumer.class);

    @AfterEach
    void clear() {
        DuressContextHolder.clear();
    }

    @Test
    void normalSessionUsesRealImplementation() {
        runner.run(context -> {
            DuressContextHolder.set(DuressContext.normal("s1"));
            assertThat(context.getBean(Consumer.class).greeter.greet()).isEqualTo("real");
        });
    }

    @Test
    void duressSessionIsRoutedToDecoy() {
        runner.run(context -> {
            DuressContextHolder.set(DuressContext.duress("s1", ActivationTrigger.DURESS_CREDENTIAL, Instant.now()));
            assertThat(context.getBean(Consumer.class).greeter.greet()).isEqualTo("decoy");
        });
    }

    @Test
    void decoyIsNeverInjectedByInterface() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            Greeter injected = context.getBean(Consumer.class).greeter;
            assertThat(injected).isNotInstanceOf(DecoyGreeter.class);
        });
    }

    @Test
    void disabledPropertyStopsRoutingButKeepsDecoyHidden() {
        runner.withPropertyValues("decoy-guard.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(io.github.valossa515.decoyguard.core.DuressActivator.class);
            DuressContextHolder.set(DuressContext.duress("s1", ActivationTrigger.DURESS_CREDENTIAL, Instant.now()));
            assertThat(context.getBean(Consumer.class).greeter.greet()).isEqualTo("real");
        });
    }

    @Test
    void twoDecoysForTheSameInterfaceFailFast() {
        runner.withBean("anotherDecoy", DecoyGreeter.class, DecoyGreeter::new).run(context ->
                assertThat(context).hasFailed());
    }
}

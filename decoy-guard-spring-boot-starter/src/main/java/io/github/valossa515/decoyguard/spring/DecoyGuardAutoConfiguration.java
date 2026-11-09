package io.github.valossa515.decoyguard.spring;

import io.github.valossa515.decoyguard.core.DuressActivator;
import io.github.valossa515.decoyguard.core.DuressSessionRegistry;
import io.github.valossa515.decoyguard.core.HeldTransactionStore;
import io.github.valossa515.decoyguard.core.InMemoryDuressSessionRegistry;
import io.github.valossa515.decoyguard.core.InMemoryHeldTransactionStore;
import io.github.valossa515.decoyguard.core.InMemoryLatencyProfile;
import io.github.valossa515.decoyguard.core.LatencyProfile;
import io.github.valossa515.decoyguard.core.LatencyShaper;
import io.github.valossa515.decoyguard.core.SilentAlertPublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/**
 * Wires Decoy Guard into a Spring Boot application.
 * Every SPI bean backs off when the application defines its own.
 */
@AutoConfiguration
public class DecoyGuardAutoConfiguration {

    static final String ENABLED_PROPERTY = "decoy-guard.enabled";

    /** Always registered: even with Decoy Guard disabled, decoy beans must stay out of injection points. */
    @Bean
    static DecoyRoutingPostProcessor decoyRoutingPostProcessor(Environment environment) {
        return new DecoyRoutingPostProcessor(environment.getProperty(ENABLED_PROPERTY, Boolean.class, true));
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "decoy-guard", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class CoreConfiguration {

        @Bean
        @ConditionalOnMissingBean
        DuressSessionRegistry duressSessionRegistry() {
            return new InMemoryDuressSessionRegistry();
        }

        @Bean
        @ConditionalOnMissingBean
        HeldTransactionStore heldTransactionStore() {
            return new InMemoryHeldTransactionStore();
        }

        @Bean
        @ConditionalOnMissingBean
        SilentAlertPublisher silentAlertPublisher() {
            return new LoggingSilentAlertPublisher();
        }

        @Bean
        @ConditionalOnMissingBean
        DuressActivator duressActivator(DuressSessionRegistry registry, SilentAlertPublisher silentAlertPublisher) {
            return new DuressActivator(registry, silentAlertPublisher);
        }
    }

    /** Response time parity: real calls are observed, decoy calls are held for a similar time. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "decoy-guard", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class LatencyConfiguration {

        @Configuration(proxyBeanMethods = false)
        @ConditionalOnProperty(prefix = "decoy-guard.latency-shaping", name = "enabled", havingValue = "true",
                matchIfMissing = true)
        @EnableConfigurationProperties(LatencyShapingProperties.class)
        static class ShapingConfiguration {

            @Bean
            @ConditionalOnMissingBean
            LatencyProfile latencyProfile(LatencyShapingProperties properties) {
                return new InMemoryLatencyProfile(properties.windowSize(), properties.minObservations());
            }

            @Bean
            @ConditionalOnMissingBean
            LatencyShaper latencyShaper(LatencyProfile profile, LatencyShapingProperties properties) {
                return new LatencyShaper(profile, properties.fallbackMin(), properties.fallbackMax());
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "decoy-guard", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.filter.OncePerRequestFilter")
    static class ServletConfiguration {

        @Bean
        @ConditionalOnMissingBean
        SessionIdExtractor sessionIdExtractor() {
            return new BearerTokenSessionIdExtractor();
        }

        @Bean
        FilterRegistrationBean<DuressContextFilter> duressContextFilter(SessionIdExtractor extractor,
                                                                        DuressSessionRegistry registry) {
            FilterRegistrationBean<DuressContextFilter> registration =
                    new FilterRegistrationBean<>(new DuressContextFilter(extractor, registry));
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
            return registration;
        }
    }
}

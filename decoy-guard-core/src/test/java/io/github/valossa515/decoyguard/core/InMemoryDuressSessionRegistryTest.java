package io.github.valossa515.decoyguard.core;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryDuressSessionRegistryTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-08T03:00:00Z"), ZoneOffset.UTC);
    private final InMemoryDuressSessionRegistry registry = new InMemoryDuressSessionRegistry(clock);

    @Test
    void unknownSessionIsNormal() {
        assertThat(registry.resolve("s1").mode()).isEqualTo(SessionMode.NORMAL);
    }

    @Test
    void missingSessionIdIsAnonymousNormal() {
        assertThat(registry.resolve(null)).isEqualTo(DuressContext.anonymous());
    }

    @Test
    void activatedSessionIsDuress() {
        registry.activate("s1", ActivationTrigger.DURESS_CREDENTIAL);

        DuressContext context = registry.resolve("s1");

        assertThat(context.isDuress()).isTrue();
        assertThat(context.trigger()).isEqualTo(ActivationTrigger.DURESS_CREDENTIAL);
        assertThat(context.activatedAt()).isEqualTo(clock.instant());
    }

    @Test
    void firstActivationWins() {
        registry.activate("s1", ActivationTrigger.RISK_SIGNAL);
        registry.activate("s1", ActivationTrigger.MANUAL);

        assertThat(registry.resolve("s1").trigger()).isEqualTo(ActivationTrigger.RISK_SIGNAL);
    }

    @Test
    void releaseReturnsSessionToNormal() {
        registry.activate("s1", ActivationTrigger.MANUAL);
        registry.release("s1");

        assertThat(registry.resolve("s1").isDuress()).isFalse();
    }
}

package io.github.valossa515.decoyguard.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DuressActivatorTest {

    private final InMemoryDuressSessionRegistry registry = new InMemoryDuressSessionRegistry();
    private final List<SilentAlert> alerts = new ArrayList<>();

    @Test
    void raisesExactlyOneAlertPerActivation() {
        DuressActivator activator = new DuressActivator(registry, alerts::add);

        activator.activate("s1", ActivationTrigger.DURESS_CREDENTIAL);
        activator.activate("s1", ActivationTrigger.DURESS_CREDENTIAL);

        assertThat(alerts).singleElement()
                .satisfies(alert -> assertThat(alert.sessionId()).isEqualTo("s1"));
    }

    @Test
    void failingAlertChannelDoesNotBreakActivation() {
        DuressActivator activator = new DuressActivator(registry, alert -> {
            throw new IllegalStateException("alert channel down");
        });

        DuressContext context = activator.activate("s1", ActivationTrigger.RISK_SIGNAL);

        assertThat(context.isDuress()).isTrue();
        assertThat(registry.resolve("s1").isDuress()).isTrue();
    }
}

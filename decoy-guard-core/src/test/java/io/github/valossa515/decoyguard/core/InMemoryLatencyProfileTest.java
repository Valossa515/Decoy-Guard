package io.github.valossa515.decoyguard.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** FT-003. Covers AC-07 and the cold start half of AC-03. */
class InMemoryLatencyProfileTest {

    private static final String PIX = "AccountService#pix";
    private static final String BALANCE = "AccountService#balance";

    /** Walks through the window in order, so every stored value is seen. */
    private static RandomGenerator sequential() {
        AtomicLong next = new AtomicLong();
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return next.getAndIncrement();
            }

            @Override
            public int nextInt(int bound) {
                return (int) (next.getAndIncrement() % bound);
            }
        };
    }

    private static Set<Duration> drain(LatencyProfile profile, String operation, CallOutcome outcome, int draws) {
        Set<Duration> seen = new HashSet<>();
        for (int i = 0; i < draws; i++) {
            seen.add(profile.sample(operation, outcome).orElseThrow());
        }
        return seen;
    }

    @Test
    void windowIsBoundedAndKeepsTheMostRecent() {
        // AC-07
        LatencyProfile profile = new InMemoryLatencyProfile(3, 1, sequential());
        for (int millis = 10; millis <= 50; millis += 10) {
            profile.record(PIX, CallOutcome.RETURNED, Duration.ofMillis(millis));
        }

        assertThat(drain(profile, PIX, CallOutcome.RETURNED, 12)).containsExactlyInAnyOrder(
                Duration.ofMillis(30), Duration.ofMillis(40), Duration.ofMillis(50));
    }

    @Test
    void operationsAndOutcomesAreIndependent() {
        // AC-07
        LatencyProfile profile = new InMemoryLatencyProfile(3, 1, sequential());
        profile.record(PIX, CallOutcome.RETURNED, Duration.ofMillis(300));
        profile.record(PIX, CallOutcome.THREW, Duration.ofMillis(80));
        profile.record(BALANCE, CallOutcome.RETURNED, Duration.ofMillis(120));

        assertThat(drain(profile, PIX, CallOutcome.RETURNED, 5)).containsExactly(Duration.ofMillis(300));
        assertThat(drain(profile, PIX, CallOutcome.THREW, 5)).containsExactly(Duration.ofMillis(80));
        assertThat(drain(profile, BALANCE, CallOutcome.RETURNED, 5)).containsExactly(Duration.ofMillis(120));
        assertThat(profile.sample(BALANCE, CallOutcome.THREW)).isEmpty();
    }

    @Test
    void emptyBelowTheMinimumNumberOfObservations() {
        // AC-03: until there are enough observations the caller falls back to the configured range
        LatencyProfile profile = new InMemoryLatencyProfile(10, 3, sequential());
        profile.record(PIX, CallOutcome.RETURNED, Duration.ofMillis(200));
        profile.record(PIX, CallOutcome.RETURNED, Duration.ofMillis(200));
        assertThat(profile.sample(PIX, CallOutcome.RETURNED)).isEmpty();

        profile.record(PIX, CallOutcome.RETURNED, Duration.ofMillis(200));
        assertThat(profile.sample(PIX, CallOutcome.RETURNED)).contains(Duration.ofMillis(200));
    }

    @Test
    void neverSamplesFromAnEmptyWindow() {
        LatencyProfile profile = new InMemoryLatencyProfile(10, 0, sequential());

        assertThat(profile.sample(PIX, CallOutcome.RETURNED)).isEmpty();
    }

    @Test
    void rejectsInvalidSizes() {
        assertThatIllegalArgumentException().isThrownBy(() -> new InMemoryLatencyProfile(0, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new InMemoryLatencyProfile(1, -1));
    }
}

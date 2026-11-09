package io.github.valossa515.decoyguard.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FT-003. Unit level proof of AC-01 to AC-06 and AC-09, with a fake clock and no real sleeping. */
class LatencyShaperTest {

    private static final String PIX = "AccountService#pix";

    /** Fake monotonic clock. Sleeping moves it forward, like real time would. */
    private final AtomicLong nanos = new AtomicLong(1_000);
    private final List<Duration> sleeps = new ArrayList<>();
    private final InMemoryLatencyProfile profile = new InMemoryLatencyProfile(10, 1);

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    private LatencyShaper shaper(LatencyProfile profile, long fallbackMinMillis, long fallbackMaxMillis) {
        return new LatencyShaper(profile, Duration.ofMillis(fallbackMinMillis), Duration.ofMillis(fallbackMaxMillis),
                nanos::get,
                duration -> {
                    sleeps.add(duration);
                    nanos.addAndGet(duration.toNanos());
                },
                () -> 0L);
    }

    private LatencyShaper.Call taking(long millis, Object result) {
        return () -> {
            nanos.addAndGet(Duration.ofMillis(millis).toNanos());
            return result;
        };
    }

    private LatencyShaper.Call failing(long millis, RuntimeException failure) {
        return () -> {
            nanos.addAndGet(Duration.ofMillis(millis).toNanos());
            throw failure;
        };
    }

    @Test
    void observeRecordsTheDurationAndAddsNoDelay() throws Throwable {
        // AC-01
        Object result = shaper(profile, 0, 0).observe(PIX, taking(200, "receipt"));

        assertThat(result).isEqualTo("receipt");
        assertThat(sleeps).isEmpty();
        assertThat(profile.sample(PIX, CallOutcome.RETURNED)).contains(Duration.ofMillis(200));
    }

    @Test
    void observeRecordsFailedCallsSeparatelyAndRethrows() {
        // AC-01, AC-04
        IllegalStateException failure = new IllegalStateException("Saldo insuficiente");

        assertThatThrownBy(() -> shaper(profile, 0, 0).observe(PIX, failing(80, failure))).isSameAs(failure);

        assertThat(sleeps).isEmpty();
        assertThat(profile.sample(PIX, CallOutcome.THREW)).contains(Duration.ofMillis(80));
        assertThat(profile.sample(PIX, CallOutcome.RETURNED)).isEmpty();
    }

    @Test
    void shapeWaitsForTheRemainderOfTheSampledTarget() throws Throwable {
        // AC-02
        profile.record(PIX, CallOutcome.RETURNED, Duration.ofMillis(200));

        Object result = shaper(profile, 0, 0).shape(PIX, taking(5, "receipt"));

        assertThat(result).isEqualTo("receipt");
        assertThat(sleeps).containsExactly(Duration.ofMillis(195));
    }

    @Test
    void shapeUsesTheFallbackRangeWhileTheProfileIsEmpty() throws Throwable {
        // AC-03
        shaper(profile, 150, 400).shape(PIX, taking(0, "receipt"));

        assertThat(sleeps).hasSize(1);
        assertThat(sleeps.get(0)).isBetween(Duration.ofMillis(150), Duration.ofMillis(400));
    }

    @Test
    void shapeDelaysAFailureLikeAFailedRealCallAndRethrowsIt() {
        // AC-04
        profile.record(PIX, CallOutcome.RETURNED, Duration.ofMillis(300));
        profile.record(PIX, CallOutcome.THREW, Duration.ofMillis(80));
        IllegalStateException failure = new IllegalStateException("Saldo insuficiente");

        assertThatThrownBy(() -> shaper(profile, 0, 0).shape(PIX, failing(0, failure))).isSameAs(failure);

        assertThat(sleeps).containsExactly(Duration.ofMillis(80));
    }

    @Test
    void shapeNeverRecords() throws Throwable {
        // AC-05
        LatencyShaper shaper = shaper(profile, 50, 50);
        shaper.shape(PIX, taking(1, "receipt"));
        shaper.shape(PIX, taking(1, "receipt"));

        assertThat(profile.sample(PIX, CallOutcome.RETURNED)).isEmpty();
    }

    @Test
    void slowDecoyGetsNoExtraDelay() throws Throwable {
        // AC-06
        profile.record(PIX, CallOutcome.RETURNED, Duration.ofMillis(100));

        shaper(profile, 0, 0).shape(PIX, taking(150, "receipt"));

        assertThat(sleeps).isEmpty();
    }

    @Test
    void failingProfileNeverFailsTheCall() throws Throwable {
        // AC-09
        LatencyProfile broken = new LatencyProfile() {
            @Override
            public void record(String operation, CallOutcome outcome, Duration elapsed) {
                throw new IllegalStateException("store down");
            }

            @Override
            public Optional<Duration> sample(String operation, CallOutcome outcome) {
                throw new IllegalStateException("store down");
            }
        };
        LatencyShaper shaper = shaper(broken, 70, 70);

        assertThat(shaper.observe(PIX, taking(200, "real"))).isEqualTo("real");
        assertThat(sleeps).isEmpty();
        assertThat(shaper.shape(PIX, taking(0, "decoy"))).isEqualTo("decoy");
        assertThat(sleeps).containsExactly(Duration.ofMillis(70));
    }

    @Test
    void interruptedWaitRestoresTheFlagAndReturnsTheResult() throws Throwable {
        profile.record(PIX, CallOutcome.RETURNED, Duration.ofMillis(200));
        LatencyShaper shaper = new LatencyShaper(profile, Duration.ZERO, Duration.ZERO, nanos::get,
                duration -> {
                    throw new InterruptedException();
                },
                () -> 0L);

        assertThat(shaper.shape(PIX, taking(0, "receipt"))).isEqualTo("receipt");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void rejectsAnInvalidFallbackRange() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new LatencyShaper(profile, Duration.ofMillis(400), Duration.ofMillis(150)));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new LatencyShaper(profile, Duration.ofMillis(-1), Duration.ofMillis(150)));
    }
}

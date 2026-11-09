package io.github.valossa515.decoyguard.spring;

import io.github.valossa515.decoyguard.core.ActivationTrigger;
import io.github.valossa515.decoyguard.core.CallOutcome;
import io.github.valossa515.decoyguard.core.DuressContext;
import io.github.valossa515.decoyguard.core.DuressContextHolder;
import io.github.valossa515.decoyguard.core.InMemoryLatencyProfile;
import io.github.valossa515.decoyguard.core.LatencyProfile;
import io.github.valossa515.decoyguard.core.LatencyShaper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FT-003 response time parity through the routing proxy. Time is a fake counter and
 * waiting only moves that counter, so nothing here sleeps for real.
 */
class LatencyShapingTest {

    private static final String PAY = Teller.class.getName() + "#pay(boolean)";

    interface Teller {
        String pay(boolean reject);
    }

    /** Fake monotonic time shared by the services and the shaper. */
    static class FakeTime {
        final AtomicLong nanos = new AtomicLong(1_000);
        final List<Duration> sleeps = new ArrayList<>();

        void pass(long millis) {
            nanos.addAndGet(Duration.ofMillis(millis).toNanos());
        }
    }

    static class Rejected extends RuntimeException {
    }

    static class RealTeller implements Teller {
        private final FakeTime time;

        RealTeller(FakeTime time) {
            this.time = time;
        }

        @Override
        public String pay(boolean reject) {
            if (reject) {
                time.pass(80);
                throw new Rejected();
            }
            time.pass(300);
            return "real";
        }
    }

    @Decoy
    static class DecoyTeller implements Teller {
        static final Rejected REJECTION = new Rejected();
        private final FakeTime time;
        long takesMillis;

        DecoyTeller(FakeTime time) {
            this.time = time;
        }

        @Override
        public String pay(boolean reject) {
            time.pass(takesMillis);
            if (reject) {
                throw REJECTION;
            }
            return "decoy";
        }
    }

    /** Remembers what was recorded and answers samples from a fixed table. */
    static class RecordingProfile implements LatencyProfile {
        final List<String> recorded = new ArrayList<>();
        final Map<CallOutcome, Duration> samples = new EnumMap<>(CallOutcome.class);
        boolean broken;

        @Override
        public void record(String operation, CallOutcome outcome, Duration elapsed) {
            if (broken) {
                throw new IllegalStateException("store down");
            }
            recorded.add(operation + " " + outcome + " " + elapsed.toMillis());
        }

        @Override
        public Optional<Duration> sample(String operation, CallOutcome outcome) {
            if (broken) {
                throw new IllegalStateException("store down");
            }
            return operation.equals(PAY) ? Optional.ofNullable(samples.get(outcome)) : Optional.empty();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Tellers {
        @Bean
        FakeTime fakeTime() {
            return new FakeTime();
        }

        @Bean
        Teller realTeller(FakeTime time) {
            return new RealTeller(time);
        }

        @Bean
        DecoyTeller decoyTeller(FakeTime time) {
            return new DecoyTeller(time);
        }
    }

    /** Replaces the default profile and gives the shaper the fake time. */
    @Configuration(proxyBeanMethods = false)
    static class FakeLatency {
        @Bean
        RecordingProfile latencyProfile() {
            return new RecordingProfile();
        }

        @Bean
        LatencyShaper latencyShaper(LatencyProfile profile, LatencyShapingProperties properties, FakeTime time) {
            return new LatencyShaper(profile, properties.fallbackMin(), properties.fallbackMax(),
                    time.nanos::get,
                    duration -> {
                        time.sleeps.add(duration);
                        time.nanos.addAndGet(duration.toNanos());
                    },
                    () -> 0L);
        }
    }

    static class Counter {
        final Teller teller;

        Counter(Teller teller) {
            this.teller = teller;
        }
    }

    private final ApplicationContextRunner plain = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DecoyGuardAutoConfiguration.class))
            .withUserConfiguration(Tellers.class)
            .withBean(Counter.class);

    private final ApplicationContextRunner runner = plain.withUserConfiguration(FakeLatency.class);

    @AfterEach
    void clear() {
        DuressContextHolder.clear();
    }

    private static void normal() {
        DuressContextHolder.set(DuressContext.normal("s1"));
    }

    private static void duress() {
        DuressContextHolder.set(DuressContext.duress("s1", ActivationTrigger.DURESS_CREDENTIAL, Instant.now()));
    }

    @Test
    void normalCallIsObservedAndNotDelayed() {
        // AC-01
        runner.run(context -> {
            normal();
            Teller teller = context.getBean(Counter.class).teller;

            assertThat(teller.pay(false)).isEqualTo("real");
            assertThatThrownBy(() -> teller.pay(true)).isInstanceOf(Rejected.class);

            assertThat(context.getBean(FakeTime.class).sleeps).isEmpty();
            assertThat(context.getBean(RecordingProfile.class).recorded)
                    .containsExactly(PAY + " RETURNED 300", PAY + " THREW 80");
        });
    }

    @Test
    void duressCallWaitsForTheSampledTarget() {
        // AC-02
        runner.run(context -> {
            context.getBean(RecordingProfile.class).samples.put(CallOutcome.RETURNED, Duration.ofMillis(200));
            duress();

            assertThat(context.getBean(Counter.class).teller.pay(false)).isEqualTo("decoy");

            assertThat(context.getBean(FakeTime.class).sleeps).containsExactly(Duration.ofMillis(200));
        });
    }

    @Test
    void duressCallUsesTheFallbackRangeBeforeEnoughObservations() {
        // AC-03
        runner.withPropertyValues("decoy-guard.latency-shaping.fallback-min=60ms",
                "decoy-guard.latency-shaping.fallback-max=90ms").run(context -> {
            duress();

            assertThat(context.getBean(Counter.class).teller.pay(false)).isEqualTo("decoy");

            List<Duration> sleeps = context.getBean(FakeTime.class).sleeps;
            assertThat(sleeps).hasSize(1);
            assertThat(sleeps.get(0)).isBetween(Duration.ofMillis(60), Duration.ofMillis(90));
        });
    }

    @Test
    void decoyExceptionIsDelayedLikeAFailedRealCallAndRethrownUnchanged() {
        // AC-04
        runner.run(context -> {
            RecordingProfile profile = context.getBean(RecordingProfile.class);
            profile.samples.put(CallOutcome.RETURNED, Duration.ofMillis(300));
            profile.samples.put(CallOutcome.THREW, Duration.ofMillis(80));
            duress();

            assertThatThrownBy(() -> context.getBean(Counter.class).teller.pay(true))
                    .isSameAs(DecoyTeller.REJECTION);

            assertThat(context.getBean(FakeTime.class).sleeps).containsExactly(Duration.ofMillis(80));
        });
    }

    @Test
    void duressCallsAreNotObserved() {
        // AC-05
        runner.run(context -> {
            duress();
            Teller teller = context.getBean(Counter.class).teller;
            teller.pay(false);
            teller.pay(false);

            assertThat(context.getBean(RecordingProfile.class).recorded).isEmpty();
        });
    }

    @Test
    void slowDecoyGetsNoExtraDelay() {
        // AC-06
        runner.run(context -> {
            context.getBean(RecordingProfile.class).samples.put(CallOutcome.RETURNED, Duration.ofMillis(100));
            context.getBean(DecoyTeller.class).takesMillis = 150;
            duress();

            assertThat(context.getBean(Counter.class).teller.pay(false)).isEqualTo("decoy");

            assertThat(context.getBean(FakeTime.class).sleeps).isEmpty();
        });
    }

    @Test
    void shapingDisabledByPropertyKeepsRouting() {
        // AC-08
        plain.withPropertyValues("decoy-guard.latency-shaping.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(LatencyShaper.class);
            assertThat(context).doesNotHaveBean(LatencyProfile.class);
            Teller teller = context.getBean(Counter.class).teller;

            normal();
            assertThat(teller.pay(false)).isEqualTo("real");
            duress();
            assertThat(teller.pay(false)).isEqualTo("decoy");
        });
    }

    @Test
    void starterDisabledRegistersNoLatencyBeans() {
        // AC-08
        plain.withPropertyValues("decoy-guard.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(LatencyShaper.class);
            assertThat(context).doesNotHaveBean(LatencyProfile.class);
        });
    }

    @Test
    void latencyProfileFailureDoesNotFailTheCall() {
        // AC-09
        runner.withPropertyValues("decoy-guard.latency-shaping.fallback-min=70ms",
                "decoy-guard.latency-shaping.fallback-max=70ms").run(context -> {
            context.getBean(RecordingProfile.class).broken = true;
            Teller teller = context.getBean(Counter.class).teller;

            normal();
            assertThat(teller.pay(false)).isEqualTo("real");
            duress();
            assertThat(teller.pay(false)).isEqualTo("decoy");

            assertThat(context.getBean(FakeTime.class).sleeps).containsExactly(Duration.ofMillis(70));
        });
    }

    @Test
    void defaultsAreRegisteredAndBackOffForUserBeans() {
        plain.run(context -> {
            assertThat(context).hasSingleBean(LatencyShaper.class);
            assertThat(context.getBean(LatencyProfile.class)).isInstanceOf(InMemoryLatencyProfile.class);
        });
        runner.run(context ->
                assertThat(context.getBean(LatencyProfile.class)).isInstanceOf(RecordingProfile.class));
    }

    @Test
    void invalidPropertiesFailStartup() {
        plain.withPropertyValues("decoy-guard.latency-shaping.window-size=0").run(context ->
                assertThat(context).hasFailed());
        plain.withPropertyValues("decoy-guard.latency-shaping.fallback-min=500ms",
                "decoy-guard.latency-shaping.fallback-max=100ms").run(context ->
                assertThat(context).hasFailed());
    }
}

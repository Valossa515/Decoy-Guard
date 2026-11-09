package io.github.valossa515.decoyguard.core;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;

/**
 * Makes decoy calls take about as long as the real calls they stand in for.
 * Real calls are timed and recorded with {@link #observe}; decoy calls go through
 * {@link #shape}, which holds the result until a duration drawn from the real
 * observations has passed.
 *
 * <p>Real calls are never delayed. A failing {@link LatencyProfile} never fails a call:
 * observation is skipped and shaping uses the fallback range.
 */
public class LatencyShaper {

    private static final System.Logger LOG = System.getLogger(LatencyShaper.class.getName());

    private final LatencyProfile profile;
    private final long fallbackMinNanos;
    private final long fallbackMaxNanos;
    private final LongSupplier nanoTime;
    private final Sleeper sleeper;
    private final RandomGenerator random;

    /** A call on a routed interface, real or decoy. */
    @FunctionalInterface
    public interface Call {
        Object proceed() throws Throwable;
    }

    public LatencyShaper(LatencyProfile profile, Duration fallbackMin, Duration fallbackMax) {
        this(profile, fallbackMin, fallbackMax, System::nanoTime, Thread::sleep,
                () -> ThreadLocalRandom.current().nextLong());
    }

    /**
     * @param profile     where real durations are stored and sampled
     * @param fallbackMin lower bound of the target used while the profile has too few observations
     * @param fallbackMax upper bound of that target
     * @param nanoTime    monotonic time source in nanoseconds
     * @param sleeper     how to wait
     * @param random      source of randomness for the fallback range
     */
    public LatencyShaper(LatencyProfile profile, Duration fallbackMin, Duration fallbackMax,
                         LongSupplier nanoTime, Sleeper sleeper, RandomGenerator random) {
        this.profile = Objects.requireNonNull(profile, "profile");
        this.fallbackMinNanos = Objects.requireNonNull(fallbackMin, "fallbackMin").toNanos();
        this.fallbackMaxNanos = Objects.requireNonNull(fallbackMax, "fallbackMax").toNanos();
        if (fallbackMinNanos < 0 || fallbackMinNanos > fallbackMaxNanos) {
            throw new IllegalArgumentException("fallback range must satisfy 0 <= min <= max");
        }
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * Runs a real call, records how long it took and returns or rethrows its outcome. Adds no delay.
     */
    public Object observe(String operation, Call call) throws Throwable {
        long start = nanoTime.getAsLong();
        Object result;
        try {
            result = call.proceed();
        } catch (Throwable thrown) {
            record(operation, CallOutcome.THREW, start);
            throw thrown;
        }
        record(operation, CallOutcome.RETURNED, start);
        return result;
    }

    /**
     * Runs a decoy call and holds its outcome until a duration typical of the real call
     * with the same outcome has passed. The result or exception is passed on unchanged.
     * Nothing is recorded.
     */
    public Object shape(String operation, Call call) throws Throwable {
        long start = nanoTime.getAsLong();
        Object result;
        try {
            result = call.proceed();
        } catch (Throwable thrown) {
            hold(operation, CallOutcome.THREW, start);
            throw thrown;
        }
        hold(operation, CallOutcome.RETURNED, start);
        return result;
    }

    private void record(String operation, CallOutcome outcome, long start) {
        try {
            profile.record(operation, outcome, Duration.ofNanos(nanoTime.getAsLong() - start));
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Call duration could not be recorded", e);
        }
    }

    private void hold(String operation, CallOutcome outcome, long start) {
        long target = target(operation, outcome);
        long remaining = target - (nanoTime.getAsLong() - start);
        if (remaining <= 0) {
            return;
        }
        try {
            sleeper.sleep(Duration.ofNanos(remaining));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private long target(String operation, CallOutcome outcome) {
        Optional<Duration> sampled;
        try {
            sampled = profile.sample(operation, outcome);
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Call duration could not be sampled, using the fallback range", e);
            sampled = Optional.empty();
        }
        if (sampled.isPresent()) {
            return sampled.get().toNanos();
        }
        if (fallbackMinNanos == fallbackMaxNanos) {
            return fallbackMinNanos;
        }
        return random.nextLong(fallbackMinNanos, fallbackMaxNanos + 1);
    }
}

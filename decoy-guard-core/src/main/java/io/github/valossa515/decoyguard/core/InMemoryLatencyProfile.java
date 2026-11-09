package io.github.valossa515.decoyguard.core;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Keeps the most recent durations per operation and outcome in a bounded window and
 * samples one of them at random. Single instance only: fine for tests, demos and
 * single-node deployments.
 */
public class InMemoryLatencyProfile implements LatencyProfile {

    public static final int DEFAULT_WINDOW_SIZE = 500;
    public static final int DEFAULT_MIN_OBSERVATIONS = 20;

    private final Map<Key, Window> windows = new ConcurrentHashMap<>();
    private final int windowSize;
    private final int minObservations;
    private final RandomGenerator random;

    public InMemoryLatencyProfile() {
        this(DEFAULT_WINDOW_SIZE, DEFAULT_MIN_OBSERVATIONS);
    }

    public InMemoryLatencyProfile(int windowSize, int minObservations) {
        this(windowSize, minObservations, () -> ThreadLocalRandom.current().nextLong());
    }

    /**
     * @param windowSize      how many of the most recent durations are kept per operation and outcome
     * @param minObservations observations needed before {@link #sample} returns a value
     * @param random          source of randomness, injectable for tests
     */
    public InMemoryLatencyProfile(int windowSize, int minObservations, RandomGenerator random) {
        if (windowSize < 1) {
            throw new IllegalArgumentException("windowSize must be at least 1");
        }
        if (minObservations < 0) {
            throw new IllegalArgumentException("minObservations must not be negative");
        }
        this.windowSize = windowSize;
        this.minObservations = minObservations;
        this.random = Objects.requireNonNull(random, "random");
    }

    @Override
    public void record(String operation, CallOutcome outcome, Duration elapsed) {
        Objects.requireNonNull(elapsed, "elapsed");
        windows.computeIfAbsent(new Key(operation, outcome), key -> new Window(windowSize))
                .add(Math.max(0, elapsed.toNanos()));
    }

    @Override
    public Optional<Duration> sample(String operation, CallOutcome outcome) {
        Window window = windows.get(new Key(operation, outcome));
        if (window == null) {
            return Optional.empty();
        }
        return window.sample(Math.max(1, minObservations), random);
    }

    private record Key(String operation, CallOutcome outcome) {
        Key {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    /** Fixed-size ring buffer of durations in nanoseconds. */
    private static final class Window {

        private final long[] nanos;
        private int next;
        private int size;

        Window(int capacity) {
            this.nanos = new long[capacity];
        }

        synchronized void add(long value) {
            nanos[next] = value;
            next = (next + 1) % nanos.length;
            if (size < nanos.length) {
                size++;
            }
        }

        synchronized Optional<Duration> sample(int minObservations, RandomGenerator random) {
            if (size < minObservations) {
                return Optional.empty();
            }
            return Optional.of(Duration.ofNanos(nanos[random.nextInt(size)]));
        }
    }
}

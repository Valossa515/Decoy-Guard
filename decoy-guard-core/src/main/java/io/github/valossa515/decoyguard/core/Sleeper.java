package io.github.valossa515.decoyguard.core;

import java.time.Duration;

/**
 * Waits for a duration. Exists so that tests can observe the wait instead of sleeping.
 */
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;
}

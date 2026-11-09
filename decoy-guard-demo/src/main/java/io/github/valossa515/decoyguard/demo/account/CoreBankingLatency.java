package io.github.valossa515.decoyguard.demo.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stands in for the network hop to core banking or to the Pix rail. The demo's real
 * account lives in memory, so without this it would answer as fast as the decoy and
 * there would be no response time to match.
 */
@Component
public class CoreBankingLatency {

    private final long minMillis;
    private final long maxMillis;

    public CoreBankingLatency(@Value("${demo.core-banking-latency.min:120ms}") Duration min,
                              @Value("${demo.core-banking-latency.max:350ms}") Duration max) {
        if (min.isNegative() || min.compareTo(max) > 0) {
            throw new IllegalArgumentException("demo.core-banking-latency.min must not be negative or above max");
        }
        this.minMillis = min.toMillis();
        this.maxMillis = max.toMillis();
    }

    /** Waits for one simulated hop. */
    public void hop() {
        long millis = minMillis == maxMillis ? minMillis : ThreadLocalRandom.current().nextLong(minMillis, maxMillis + 1);
        if (millis == 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

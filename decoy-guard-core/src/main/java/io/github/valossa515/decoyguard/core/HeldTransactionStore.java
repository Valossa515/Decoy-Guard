package io.github.valossa515.decoyguard.core;

import java.util.List;

/**
 * SPI that keeps transactions intercepted during duress sessions,
 * so the fraud team and the user can review them later.
 */
public interface HeldTransactionStore {

    HeldTransaction hold(HeldTransaction transaction);

    List<HeldTransaction> findBySession(String sessionId);
}

package io.github.valossa515.decoyguard.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Single-instance store. Fine for tests and demos, not for production.
 */
public class InMemoryHeldTransactionStore implements HeldTransactionStore {

    private final Map<String, List<HeldTransaction>> bySession = new ConcurrentHashMap<>();

    @Override
    public HeldTransaction hold(HeldTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        bySession.computeIfAbsent(transaction.sessionId(), id -> new CopyOnWriteArrayList<>()).add(transaction);
        return transaction;
    }

    @Override
    public List<HeldTransaction> findBySession(String sessionId) {
        return List.copyOf(bySession.getOrDefault(sessionId, List.of()));
    }
}

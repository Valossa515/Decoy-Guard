package io.github.valossa515.decoyguard.core;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A transaction the user was coerced into making. It was shown as successful on screen
 * but never reached the real ledger or payment rail.
 *
 * @param id           identifier shown to the user (receipt id); unrelated to any real rail id
 * @param sessionId    session that requested it
 * @param type         free-form type, for example PIX or TED
 * @param amount       requested amount
 * @param counterparty destination as typed by the user, kept for the fraud team
 * @param heldAt       when it was intercepted
 */
public record HeldTransaction(String id, String sessionId, String type, BigDecimal amount,
                              String counterparty, Instant heldAt) {

    public HeldTransaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(heldAt, "heldAt");
    }
}

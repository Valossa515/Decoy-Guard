package io.github.valossa515.decoyguard.demo.account;

/**
 * Thrown by both the real and the decoy account, so the error looks the same in either mode.
 */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException() {
        super("Saldo insuficiente");
    }
}

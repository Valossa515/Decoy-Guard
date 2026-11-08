package io.github.valossa515.decoyguard.demo.account;

import java.security.SecureRandom;

/**
 * Generates ids shaped like Pix end-to-end ids (E + 31 alphanumerics).
 * Shared by the real and the decoy account so receipts are indistinguishable.
 */
final class EndToEndIds {

    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private EndToEndIds() {
    }

    static String next() {
        StringBuilder id = new StringBuilder("E");
        for (int i = 0; i < 31; i++) {
            id.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return id.toString();
    }
}

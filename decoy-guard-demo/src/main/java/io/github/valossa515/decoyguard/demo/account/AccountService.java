package io.github.valossa515.decoyguard.demo.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * What the bank app screen shows. Controllers depend only on this interface,
 * so they never know whether they are talking to the real account or the decoy.
 */
public interface AccountService {

    Balance balance();

    List<StatementEntry> statement();

    PixReceipt pix(PixRequest request);

    /**
     * The receipt of a Pix of this account with its current status.
     *
     * @throws PixNotFoundException when the account has no Pix with that id
     */
    PixReceipt findPix(String endToEndId);

    enum PixStatus {
        EM_PROCESSAMENTO,
        CONCLUIDO
    }

    record Balance(BigDecimal available) {
    }

    record StatementEntry(LocalDate date, String description, BigDecimal amount) {
    }

    record PixRequest(String key, BigDecimal amount) {
    }

    record PixReceipt(String endToEndId, String key, BigDecimal amount, Instant at, PixStatus status) {

        public PixReceipt withStatus(PixStatus newStatus) {
            return new PixReceipt(endToEndId, key, amount, at, newStatus);
        }
    }
}

package io.github.valossa515.decoyguard.demo.account;

import io.github.valossa515.decoyguard.core.DuressContextHolder;
import io.github.valossa515.decoyguard.core.HeldTransaction;
import io.github.valossa515.decoyguard.core.HeldTransactionStore;
import io.github.valossa515.decoyguard.spring.Decoy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * What the attacker sees. A small, believable balance and history, and Pix transfers that
 * produce a normal looking receipt while the money never leaves the real account.
 *
 * <p>The decoy balance goes down after each held transfer, because an attacker who checks
 * the balance after a "successful" Pix expects it to drop.
 *
 * <p>A held Pix stays {@code EM_PROCESSAMENTO} for good, like a real Pix stalled in anti-fraud
 * review. That gives the attacker a reason for the money not having arrived.
 */
@Decoy
@Service
public class DecoyAccountService implements AccountService {

    private static final BigDecimal DECOY_OPENING_BALANCE = new BigDecimal("312.47");
    private static final String PIX = "PIX";

    private final HeldTransactionStore heldTransactions;
    private final Clock clock;

    public DecoyAccountService(HeldTransactionStore heldTransactions, Clock clock) {
        this.heldTransactions = heldTransactions;
        this.clock = clock;
    }

    @Override
    public Balance balance() {
        return new Balance(DECOY_OPENING_BALANCE.subtract(heldTotal()));
    }

    @Override
    public List<StatementEntry> statement() {
        LocalDate today = LocalDate.now(clock);
        List<StatementEntry> entries = new ArrayList<>();
        entries.add(new StatementEntry(today.minusDays(8), "Mercado Dia", new BigDecimal("-64.90")));
        entries.add(new StatementEntry(today.minusDays(5), "Conta de luz", new BigDecimal("-142.18")));
        entries.add(new StatementEntry(today.minusDays(2), "Pix recebido", new BigDecimal("150.00")));
        for (HeldTransaction held : heldForSession()) {
            entries.add(new StatementEntry(LocalDate.ofInstant(held.heldAt(), clock.getZone()),
                    "Pix enviado " + held.counterparty(), held.amount().negate()));
        }
        return entries;
    }

    @Override
    public PixReceipt pix(PixRequest request) {
        if (request.amount().compareTo(balance().available()) > 0) {
            throw new InsufficientFundsException();
        }
        String sessionId = DuressContextHolder.current().sessionId();
        Instant now = clock.instant();
        HeldTransaction held = heldTransactions.hold(new HeldTransaction(EndToEndIds.next(), sessionId, PIX,
                request.amount(), request.key(), now));
        return receiptOf(held);
    }

    @Override
    public PixReceipt findPix(String endToEndId) {
        return heldForSession().stream()
                .filter(held -> PIX.equals(held.type()) && held.id().equals(endToEndId))
                .findFirst()
                .map(DecoyAccountService::receiptOf)
                .orElseThrow(PixNotFoundException::new);
    }

    private static PixReceipt receiptOf(HeldTransaction held) {
        return new PixReceipt(held.id(), held.counterparty(), held.amount(), held.heldAt(),
                PixStatus.EM_PROCESSAMENTO);
    }

    private List<HeldTransaction> heldForSession() {
        return heldTransactions.findBySession(DuressContextHolder.current().sessionId());
    }

    private BigDecimal heldTotal() {
        return heldForSession().stream().map(HeldTransaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

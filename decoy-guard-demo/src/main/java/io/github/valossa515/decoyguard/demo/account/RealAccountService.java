package io.github.valossa515.decoyguard.demo.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The real account. In a real bank this would call the core banking system and the Pix rail.
 * Here each of those hops is simulated by {@link CoreBankingLatency}, so the real service
 * has a response time for the decoy to match.
 *
 * <p>A Pix is accepted as {@code EM_PROCESSAMENTO} and settles after {@code demo.pix-settlement}.
 * The status is derived from the clock when the Pix is consulted, so nothing is scheduled.
 */
@Service
public class RealAccountService implements AccountService {

    private final Clock clock;
    private final CoreBankingLatency latency;
    private final Duration settlement;
    private final Map<String, PixReceipt> sentPix = new HashMap<>();
    private final List<StatementEntry> entries = new ArrayList<>();
    private BigDecimal available = new BigDecimal("48750.32");

    public RealAccountService(Clock clock, CoreBankingLatency latency,
                              @Value("${demo.pix-settlement:5s}") Duration settlement) {
        if (settlement.isNegative()) {
            throw new IllegalArgumentException("demo.pix-settlement must not be negative");
        }
        this.clock = clock;
        this.latency = latency;
        this.settlement = settlement;
        LocalDate today = LocalDate.now(clock);
        entries.add(new StatementEntry(today.minusDays(9), "Aposentadoria INSS", new BigDecimal("4120.00")));
        entries.add(new StatementEntry(today.minusDays(6), "Farmacia Sao Joao", new BigDecimal("-187.40")));
        entries.add(new StatementEntry(today.minusDays(3), "Rendimento CDB", new BigDecimal("312.85")));
    }

    @Override
    public Balance balance() {
        latency.hop();
        synchronized (this) {
            return new Balance(available);
        }
    }

    @Override
    public List<StatementEntry> statement() {
        latency.hop();
        synchronized (this) {
            return List.copyOf(entries);
        }
    }

    @Override
    public PixReceipt pix(PixRequest request) {
        latency.hop();
        PixReceipt receipt = debit(request);
        // An accepted Pix also goes through the Pix rail. A rejected one never gets there.
        latency.hop();
        return receipt;
    }

    @Override
    public PixReceipt findPix(String endToEndId) {
        latency.hop();
        PixReceipt receipt = sent(endToEndId);
        return settled(receipt) ? receipt.withStatus(PixStatus.CONCLUIDO) : receipt;
    }

    private synchronized PixReceipt debit(PixRequest request) {
        if (request.amount().compareTo(available) > 0) {
            throw new InsufficientFundsException();
        }
        available = available.subtract(request.amount());
        entries.add(new StatementEntry(LocalDate.now(clock), "Pix enviado " + request.key(), request.amount().negate()));
        PixReceipt receipt = new PixReceipt(EndToEndIds.next(), request.key(), request.amount(), clock.instant(),
                PixStatus.EM_PROCESSAMENTO);
        sentPix.put(receipt.endToEndId(), receipt);
        return receipt;
    }

    private synchronized PixReceipt sent(String endToEndId) {
        PixReceipt receipt = sentPix.get(endToEndId);
        if (receipt == null) {
            throw new PixNotFoundException();
        }
        return receipt;
    }

    private boolean settled(PixReceipt receipt) {
        return !clock.instant().isBefore(receipt.at().plus(settlement));
    }
}

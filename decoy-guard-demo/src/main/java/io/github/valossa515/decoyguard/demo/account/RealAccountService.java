package io.github.valossa515.decoyguard.demo.account;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The real account. In a real bank this would call the core banking system and the Pix rail.
 */
@Service
public class RealAccountService implements AccountService {

    private final Clock clock;
    private final List<StatementEntry> entries = new ArrayList<>();
    private BigDecimal available = new BigDecimal("48750.32");

    public RealAccountService(Clock clock) {
        this.clock = clock;
        LocalDate today = LocalDate.now(clock);
        entries.add(new StatementEntry(today.minusDays(9), "Aposentadoria INSS", new BigDecimal("4120.00")));
        entries.add(new StatementEntry(today.minusDays(6), "Farmacia Sao Joao", new BigDecimal("-187.40")));
        entries.add(new StatementEntry(today.minusDays(3), "Rendimento CDB", new BigDecimal("312.85")));
    }

    @Override
    public synchronized Balance balance() {
        return new Balance(available);
    }

    @Override
    public synchronized List<StatementEntry> statement() {
        return List.copyOf(entries);
    }

    @Override
    public synchronized PixReceipt pix(PixRequest request) {
        if (request.amount().compareTo(available) > 0) {
            throw new InsufficientFundsException();
        }
        available = available.subtract(request.amount());
        entries.add(new StatementEntry(LocalDate.now(clock), "Pix enviado " + request.key(), request.amount().negate()));
        return new PixReceipt(EndToEndIds.next(), request.key(), request.amount(), clock.instant(), "CONCLUIDO");
    }
}

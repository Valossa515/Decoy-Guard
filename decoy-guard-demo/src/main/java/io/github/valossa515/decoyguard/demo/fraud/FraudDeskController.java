package io.github.valossa515.decoyguard.demo.fraud;

import io.github.valossa515.decoyguard.core.DuressContext;
import io.github.valossa515.decoyguard.core.DuressSessionRegistry;
import io.github.valossa515.decoyguard.core.HeldTransaction;
import io.github.valossa515.decoyguard.core.HeldTransactionStore;
import io.github.valossa515.decoyguard.core.SilentAlert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Stand-in for the bank's back office. In a real deployment this lives in a separate,
 * internally authenticated system, never in the customer facing API.
 */
@RestController
@RequestMapping("/fraud-desk")
public class FraudDeskController {

    private final HeldTransactionStore heldTransactions;
    private final DuressSessionRegistry sessions;

    public FraudDeskController(HeldTransactionStore heldTransactions, DuressSessionRegistry sessions) {
        this.heldTransactions = heldTransactions;
        this.sessions = sessions;
    }

    @GetMapping("/held")
    public List<HeldTransaction> held(@RequestParam String session) {
        return heldTransactions.findBySession(session);
    }

    /**
     * The silent alert raised for the session, rebuilt from the registry. Empty when the session
     * was never in duress. Reads only: it never publishes an alert.
     */
    @GetMapping("/alerts")
    public List<SilentAlert> alerts(@RequestParam String session) {
        DuressContext context = sessions.resolve(session);
        return context.isDuress() ? List.of(SilentAlert.of(context)) : List.of();
    }
}

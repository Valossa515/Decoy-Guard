package io.github.valossa515.decoyguard.demo.fraud;

import io.github.valossa515.decoyguard.core.HeldTransaction;
import io.github.valossa515.decoyguard.core.HeldTransactionStore;
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

    public FraudDeskController(HeldTransactionStore heldTransactions) {
        this.heldTransactions = heldTransactions;
    }

    @GetMapping("/held")
    public List<HeldTransaction> held(@RequestParam String session) {
        return heldTransactions.findBySession(session);
    }
}

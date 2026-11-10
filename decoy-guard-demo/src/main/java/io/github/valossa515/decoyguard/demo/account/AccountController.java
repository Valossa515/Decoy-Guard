package io.github.valossa515.decoyguard.demo.account;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Plain controller. Nothing here knows about duress mode.
 */
@RestController
@RequestMapping("/account")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/balance")
    public AccountService.Balance balance() {
        return accounts.balance();
    }

    @GetMapping("/statement")
    public List<AccountService.StatementEntry> statement() {
        return accounts.statement();
    }

    @PostMapping("/pix")
    public AccountService.PixReceipt pix(@RequestBody AccountService.PixRequest request) {
        return accounts.pix(request);
    }

    @GetMapping("/pix/{endToEndId}")
    public AccountService.PixReceipt findPix(@PathVariable String endToEndId) {
        return accounts.findPix(endToEndId);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    public Map<String, String> insufficientFunds(InsufficientFundsException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(PixNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> pixNotFound(PixNotFoundException e) {
        return Map.of("error", e.getMessage());
    }
}

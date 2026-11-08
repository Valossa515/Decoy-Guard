package io.github.valossa515.decoyguard.demo.auth;

import io.github.valossa515.decoyguard.core.ActivationTrigger;
import io.github.valossa515.decoyguard.core.DuressActivator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Demo login. The real PIN and the duress PIN produce exactly the same response;
 * only the server knows which session is a decoy.
 */
@RestController
public class AuthController {

    private final DemoSessions sessions;
    private final DuressActivator duressActivator;
    private final String realPin;
    private final String duressPin;

    public AuthController(DemoSessions sessions, DuressActivator duressActivator,
                          @Value("${demo.real-pin}") String realPin,
                          @Value("${demo.duress-pin}") String duressPin) {
        this.sessions = sessions;
        this.duressActivator = duressActivator;
        this.realPin = realPin;
        this.duressPin = duressPin;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        boolean duress = duressPin.equals(request.pin());
        if (!duress && !realPin.equals(request.pin())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        String token = sessions.open();
        if (duress) {
            duressActivator.activate(token, ActivationTrigger.DURESS_CREDENTIAL);
        }
        return new LoginResponse(token);
    }

    public record LoginRequest(String pin) {
    }

    public record LoginResponse(String token) {
    }
}

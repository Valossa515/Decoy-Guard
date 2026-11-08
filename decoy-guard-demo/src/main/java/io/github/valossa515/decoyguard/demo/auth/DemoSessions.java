package io.github.valossa515.decoyguard.demo.auth;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Toy session store for the demo. Tokens are opaque and look the same in either mode.
 */
@Component
public class DemoSessions {

    private final Set<String> tokens = ConcurrentHashMap.newKeySet();

    public String open() {
        String token = UUID.randomUUID().toString();
        tokens.add(token);
        return token;
    }

    public boolean isOpen(String token) {
        return token != null && tokens.contains(token);
    }
}

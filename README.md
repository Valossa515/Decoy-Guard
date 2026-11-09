# Decoy Guard

Anti-coercion security layer for banking apps. A duress credential opens a convincing decoy session while real transactions are held and a silent alert is sent.

[![CI](https://github.com/Valossa515/decoy-guard/actions/workflows/ci.yml/badge.svg)](https://github.com/Valossa515/decoy-guard/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-blue)
![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow)

![Demo: duress login, Pix of R$ 200 held, real account intact, alert on the fraud desk](docs/media/demo.gif)

*Left: normal session. Middle: duress session, same app and same code. Right: what only the bank sees.*

> **Status:** early prototype for research and portfolio purposes. Not production ready.

## The problem

In Brazil, elderly people are a frequent target of scams where the victim is coerced, by phone or in person, into unlocking their bank app and showing the screen or making Pix transfers. Pix is instant and hard to reverse, and the attacker is watching every step, so the victim has no safe way to say no.

Most anti-fraud controls either block the transaction (which tells the attacker the victim resisted) or ask for more confirmation (which the attacker simply demands). Neither protects someone who is being watched.

## The idea

Give the user a way to **comply on screen without complying in reality**.

1. The user registers a **duress PIN** (or a duress fingerprint) next to their normal one.
2. Under coercion, they log in with it. The app looks and behaves exactly like a normal session.
3. Behind the scenes the session is served by a **decoy**: a small, believable balance and history.
4. Transfers show a normal receipt, but the money **never leaves the account**. The transaction is held for the fraud team.
5. A **silent alert** reaches the bank and, optionally, a trusted contact.

```mermaid
sequenceDiagram
    actor U as User (coerced)
    participant A as Bank app
    participant G as Decoy Guard
    participant R as Real account
    participant D as Decoy account
    participant F as Fraud desk

    U->>A: Login with duress PIN
    A->>G: activate(session, DURESS_CREDENTIAL)
    G-->>F: Silent alert
    U->>A: Check balance / send Pix
    A->>G: AccountService.pix(...)
    G->>D: routed (session is DURESS)
    D-->>F: Transaction held
    D-->>A: Normal looking receipt
    Note over R: Never touched
```

The guiding rule: **nothing the attacker can observe may differ between a real and a decoy session.** Same response shapes, same errors, same receipt format, comparable response times.

## How it works in code

Your controllers keep depending on an interface. You write a decoy implementation and mark it with `@Decoy`:

```java
public interface AccountService {
    Balance balance();
    PixReceipt pix(PixRequest request);
}

@Service
class RealAccountService implements AccountService { /* core banking */ }

@Decoy
@Service
class DecoyAccountService implements AccountService { /* believable fake */ }
```

The starter then:

* resolves the session mode once per request (`DuressContextFilter`) and binds it to the thread;
* wraps every real bean that has a decoy in a routing proxy, so calls go to the decoy only while the session is in duress;
* hides `@Decoy` beans from injection, so a decoy can never leak into a normal session, even with the starter disabled;
* exposes `DuressActivator`, which switches a session to duress and raises exactly one silent alert.

Switching a session at login:

```java
if (duressPinMatches) {
    duressActivator.activate(sessionToken, ActivationTrigger.DURESS_CREDENTIAL);
}
```

Every storage and alert component is an SPI with an in-memory default you are expected to replace:

| SPI | Default | Replace with |
|---|---|---|
| `DuressSessionRegistry` | in memory | Redis or database shared by all instances |
| `HeldTransactionStore` | in memory | durable store read by the fraud team |
| `SilentAlertPublisher` | application log | fraud queue, trusted contact notification |
| `SessionIdExtractor` | bearer token | JWT claim, session cookie |
| `LatencyProfile` | in memory | store shared by all instances |

### Response time parity

A decoy skips core banking, so it would answer faster than the real service, and an attacker watching the screen could notice. The routing proxy times every real call per method and per outcome (returned or threw) and holds each decoy call until a duration drawn from those observations has passed. Real calls are never delayed.

| Property | Default | Meaning |
|---|---|---|
| `decoy-guard.latency-shaping.enabled` | `true` | Turns observation and shaping off when `false` |
| `decoy-guard.latency-shaping.window-size` | `500` | Most recent real durations kept per method and outcome |
| `decoy-guard.latency-shaping.min-observations` | `20` | Observations needed before they replace the fallback range |
| `decoy-guard.latency-shaping.fallback-min` | `150ms` | Lower bound of the decoy duration right after startup |
| `decoy-guard.latency-shaping.fallback-max` | `400ms` | Upper bound of that duration |

Set the fallback range to the typical latency of your backend. Three limits to keep in mind: the wait blocks the request thread for the length of a real call, a decoy that is slower than the real service is not made faster, and parity is per routed call, so a controller should make the same routed calls in both modes.

## Modules

| Module | What it is |
|---|---|
| `decoy-guard-core` | Framework-agnostic model and SPIs (Java 21, no dependencies) |
| `decoy-guard-spring-boot-starter` | Auto-configuration, `@Decoy` routing, request filter |
| `decoy-guard-demo` | Fake bank API showing both sessions side by side |

## Try the demo

Requires Java 21 and Maven.

```bash
mvn verify
mvn -pl decoy-guard-demo spring-boot:run
```

Demo PINs: `1234` (real) and `4321` (duress).

Open http://localhost:8080 for the web front end: two phones running the same bank app side by side, and the fraud desk panel. The GIF at the top of this page was recorded from it with [docs/media/record-demo.mjs](docs/media/record-demo.mjs).

Or use the API directly:

```bash
# Log in with the duress PIN. The response looks exactly like a normal login.
TOKEN=$(curl -s localhost:8080/login -H 'Content-Type: application/json' \
  -d '{"pin":"4321"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')

curl -s localhost:8080/account/balance -H "Authorization: Bearer $TOKEN"
# {"available":312.47}

curl -s localhost:8080/account/pix -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"key":"golpista@exemplo.com","amount":200}'
# {"endToEndId":"E...","status":"CONCLUIDO",...}   <- looks real, money never left

curl -s "localhost:8080/fraud-desk/held?session=$TOKEN"
# the held Pix, visible to the bank

curl -s "localhost:8080/fraud-desk/alerts?session=$TOKEN"
# the silent alert raised at login
```

Repeat with PIN `1234` to see the real account (balance 48,750.32).

The demo's real account simulates the hop to core banking (`demo.core-banking-latency`, 120 to 350 ms), so you can see both sessions answer at the same pace.

## Documentation

* [Threat model](docs/threat-model.md): what this protects, what it does not, and why
* [FT-001 Duress session routing](.agent/plans/duress-session-routing.md): first spec (plan), with its [feature spec](.agent/specs/features/duress-session-routing.spec.toon) and [acceptance criteria](.agent/specs/acceptance/duress-session-routing.acceptance.toon)
* [FT-002 Mobile-style front end](.agent/plans/mobile-front-end.md): plan, with its [feature spec](.agent/specs/features/mobile-front-end.spec.toon), [acceptance criteria](.agent/specs/acceptance/mobile-front-end.acceptance.toon) and [contract](.agent/specs/contracts/mobile-front-end.contract.toon)
* [FT-003 Response time parity](.agent/plans/response-time-parity.md): plan, with its [feature spec](.agent/specs/features/response-time-parity.spec.toon), [acceptance criteria](.agent/specs/acceptance/response-time-parity.acceptance.toon), [contract](.agent/specs/contracts/response-time-parity.contract.toon) and [architecture notes](.agent/plans/response-time-parity-architecture.md)
* [Agent context](.agent/SKILLS.md): project context and conventions for AI coding agents, following [SDD](https://github.com/valossa515/sdd)

## Roadmap

- [x] Core model, SPIs and in-memory defaults
- [x] Spring Boot starter with `@Decoy` routing
- [x] Demo bank API with held Pix and fraud desk view
- [x] Mobile-style web front end showing both sessions side by side
- [x] Response time parity (decoy latency shaped like the real backend)
- [ ] Redis `DuressSessionRegistry` and JDBC `HeldTransactionStore`
- [ ] Risk signals: active phone call, screen mirroring, remote access apps (Android module)
- [ ] Trusted contact notification channel
- [ ] Publish to Maven Central under `io.github.valossa515`

## Em português

Camada de segurança anti-coação para apps bancários. Quando a pessoa é obrigada a abrir o app na frente de um golpista, ela entra com uma senha de coação. O app parece normal, mas mostra um saldo falso e pequeno, os Pix aparecem como concluídos sem o dinheiro sair da conta, e o banco recebe um alerta silencioso. O objetivo é proteger principalmente pessoas idosas, que são as vítimas mais frequentes desse tipo de golpe.

## License

[MIT](LICENSE) © Felipe Martins

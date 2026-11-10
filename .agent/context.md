# Project Context

## Project Overview
- **Name:** Decoy Guard
- **Type:** Library (Spring Boot starter) with a demo REST API
- **Stack:** Java 21, Spring Boot 4.1.2, Spring Web MVC, Spring AOP
- **Build tool:** Maven multi-module (`decoy-guard-core`, `decoy-guard-spring-boot-starter`, `decoy-guard-demo`)
- **Java/Runtime version:** 21
- **Status:** early prototype for research and portfolio purposes, not production ready

## Problem
In Brazil, elderly people are a frequent target of scams where the victim is coerced, by phone or in person, into unlocking their bank app and showing the screen or making Pix transfers. Pix is instant and hard to reverse, and the attacker watches every step, so the victim has no safe way to say no. Blocking the transaction tells the attacker the victim resisted. Asking for more confirmation does not help, because the attacker demands it.

## Objectives
- Let a coerced user comply on screen without complying in reality.
- Serve a duress session with a convincing decoy that is indistinguishable from a real session.
- Hold every transfer made in a duress session so no money leaves the account.
- Notify the bank, and optionally a trusted contact, through a silent out-of-band alert.
- Let application code (controllers, use cases) stay unaware of the session mode.

## Personas
- **Account holder under coercion** (typically elderly): logs in with the duress PIN and follows the attacker's orders.
- **Attacker**: watches the screen or dictates steps, may have a partner checking the receiving account.
- **Bank developer**: integrates the starter, writes `@Decoy` implementations and replaces the SPI defaults.
- **Fraud team**: receives the silent alert and reviews held transactions.

## Main Flows
1. **Normal login:** `POST /login` with the real PIN returns a token. Calls reach the real services.
2. **Duress login:** `POST /login` with the duress PIN returns a token with the same shape. `DuressActivator` switches the session to DURESS and publishes one `SilentAlert`.
3. **Routed call:** `DuressContextFilter` resolves the mode per request. The routing proxy sends calls on a routed interface to the `@Decoy` bean while the session is DURESS.
4. **Held Pix:** in DURESS, `POST /account/pix` returns status `EM_PROCESSAMENTO` like a real Pix, lowers the decoy balance and stores a `HeldTransaction`. The real account is never touched. `GET /account/pix/{endToEndId}` shows a real Pix as `CONCLUIDO` after the settlement time and a held Pix as `EM_PROCESSAMENTO` for good (FT-004).
5. **Fraud desk review:** `GET /fraud-desk/held?session=` lists held transactions of a session.

## Available Endpoints
| Method | Path | Description |
|--------|------|-------------|
| POST | /login | Exchanges a PIN for an opaque token (same response for real and duress PIN) |
| GET | /account/balance | Available balance |
| GET | /account/statement | Statement entries |
| POST | /account/pix | Sends a Pix (real) or holds it (decoy) |
| GET | /fraud-desk/held | Held transactions of a session (back office stand-in, demo only) |

## Invariants (from CLAUDE.md, must not break)
1. The session mode is never observable by the client: no header, field, cookie, log line returned to the client, or different error.
2. Real and decoy implementations share DTOs, exceptions and id formats. A new method on a routed interface is implemented in both and gets a parity test in the demo.
3. `@Decoy` beans must never be injectable. Routed services are injected by interface only.
4. Alert failures must never fail the request.
5. Every new storage or delivery concern is an SPI in core with an in-memory or logging default.

## Threat Model Summary
Full document: [docs/threat-model.md](../docs/threat-model.md). Read it before changing behavior.

**Scenario.** A legitimate holder is coerced into using their own app. Credentials, device and biometrics are genuine, so classic fraud signals do not fire.

**Assets.** Balance and investments, transaction capability (Pix, TED, loans), information about wealth, the victim's physical safety.

**Attacker in scope.** Sees the screen in real time, dictates navigation and amounts, asks to open balance, statement and receipts, has a partner who can check whether a Pix arrived.

**Out of scope for now.** Malware or rooted device, network interception, bank insiders, an attacker who already knows the victim has a duress PIN and which one.

**Security goals.**

| Goal | Mechanism | Status |
|------|-----------|--------|
| Indistinguishability | Shared interface, DTOs, exceptions and receipt id format. The filter never writes the mode to the response. Decoy calls are held for a duration learned from real calls (FT-003). | Done per routed call |
| No real loss | Duress calls go to the decoy, which only records a `HeldTransaction` | Done in the demo |
| Plausibility | Decoy balance minus held transfers, decoy statement includes them | Done in the demo |
| Out-of-band alert | `DuressActivator` raises one `SilentAlert` per activation through `SilentAlertPublisher` | SPI done, real channels pending |
| Fail safe | Alert failures are caught. `@Decoy` beans are never autowire candidates, even with the starter disabled. | Done |

**Known limitations.** The receiving side eventually notices the Pix never arrived. Escalation risk if the attacker realizes the deception. Response time parity is per routed call and uses a fallback range right after startup. A frightened user may forget the second PIN. The context is thread-bound (`@Async` and reactive code must propagate it). Push, SMS, e-mail and internet banking must also respect the decoy. Holding a confirmed Pix must fit central bank rules (MED). Attackers may learn to demand "the other PIN".

**Out of scope by design.** Detecting the scam call, recovering money sent outside a duress session, replacing the bank's anti-fraud scoring.

## Constraints
- **Regulatory:** holding a Pix the user confirmed must fit the central bank's Pix rules, including MED. Needs review by people who know the regulation.
- **Technological:** Java 21, Spring Boot 4.1. `decoy-guard-core` has no runtime dependencies. Servlet stack only (no reactive support yet).
- **Security:** nothing observable by the client may differ between modes. Response time parity is handled by `LatencyShaper` (FT-003): do not add work to only one of the two paths outside routed calls.

## Dependencies
| Dependency | Purpose |
|------------|---------|
| spring-boot-autoconfigure | Starter auto-configuration |
| spring-aop | Interface-based routing proxies |
| spring-web | `OncePerRequestFilter` for the request filter |
| jakarta.servlet-api (provided) | Servlet API |
| spring-boot-starter-webmvc | Demo REST API |
| junit-jupiter, assertj-core, spring-boot-starter-test, spring-boot-starter-webmvc-test | Tests |

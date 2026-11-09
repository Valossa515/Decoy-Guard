# Architecture Decision Records

Lightweight ADRs reconstructed from the code, `CLAUDE.md` and the threat model at bootstrap time (2026-11-09).

## ADR-001 Framework-agnostic core with no runtime dependencies
- **Context:** the model and SPIs must be reusable outside Spring (future Android module, other frameworks).
- **Decision:** `decoy-guard-core` depends on nothing at runtime. It logs with `System.Logger`.
- **Consequences:** Spring-specific pieces (`SessionIdExtractor`, `LoggingSilentAlertPublisher`) live in the starter.

## ADR-002 Route per call with an interface-based JDK proxy
- **Context:** controllers and use cases must not know whether a session is real or decoy.
- **Alternatives:** `if (duress)` branches in services, request-scoped beans, CGLIB class proxies.
- **Choice:** `DecoyRoutingPostProcessor` wraps real beans in a JDK proxy that checks `DuressContextHolder` on every call.
- **Consequences:** routed services must be injected by interface, never by concrete class.

## ADR-003 Decoy beans are never autowire candidates
- **Context:** a decoy leaking into a normal session would show fake data to a real user.
- **Choice:** `@Decoy` bean definitions get `autowireCandidate=false`. The post processor is registered even when `decoy-guard.enabled=false`.
- **Consequences:** decoys are reachable only through the routing proxy.

## ADR-004 Thread-bound duress context resolved once per request
- **Context:** the mode must be available anywhere in the call stack without changing method signatures.
- **Choice:** `DuressContextFilter` resolves the mode and binds it to a `ThreadLocal`, cleared in `finally`.
- **Consequences:** `@Async` and reactive pipelines must propagate the context explicitly. Servlet stack only for now.

## ADR-005 Storage and delivery as SPIs with in-memory or logging defaults
- **Context:** banks run their own Redis, databases and alert channels.
- **Choice:** every storage or delivery concern is an SPI in core, registered with `@ConditionalOnMissingBean`.
- **Consequences:** defaults are single-instance and not for production. Redis and JDBC implementations are on the roadmap.

## ADR-006 Alert failures never fail the request
- **Context:** an error during a duress login would differ from a normal login and could tip off the attacker.
- **Choice:** `DuressActivator` catches `RuntimeException` from the publisher and logs it.
- **Consequences:** a lost alert is possible. The decoy keeps working (fail safe goal).

## ADR-007 Real and decoy share DTOs, exceptions and id formats
- **Context:** indistinguishability. Any difference in shapes, errors or ids is observable.
- **Choice:** DTOs are nested records of the routed interface, exceptions are shared, ids come from one generator (`EndToEndIds`).
- **Consequences:** a new method on a routed interface needs both implementations and a parity test in the demo.

## ADR-008 Specs follow SDD
- **Date:** 2026-11-09
- **Context:** specs lived in `docs/specs/` as a `.md` plus `.toon` pair per feature.
- **Choice:** adopt SDD. Feature specs in `.agent/specs/features`, acceptance in `.agent/specs/acceptance`, plans in `.agent/plans`. DG-001 migrated as FT-001.
- **Consequences:** `docs/specs/` removed. Specs are checked with `.agent/scripts/validate-spec.sh`.

## ADR-009 Demo front end is static and the fraud desk reads alerts from the registry
- **Date:** 2026-11-09
- **Context:** FT-002 needs a visual demo of both sessions and a fraud desk panel that shows the silent alert, which no endpoint exposed.
- **Choice:** plain HTML, CSS and JS in `decoy-guard-demo/src/main/resources/static`, no build step. Both phones are mounted by one `mountPhone(slot)` from one `<template>`. The back office gets `GET /fraud-desk/alerts?session=`, rebuilt from `DuressSessionRegistry.resolve` with `SilentAlert.of` instead of storing alerts.
- **Consequences:** no new storage SPI and no library change. The panel polls with every open token, so the page itself never knows which phone is in duress. Like `/fraud-desk/held`, the alerts endpoint reveals the mode and must live behind internal auth in a real deployment.

## ADR-010 Decoy latency is learned from real calls and applied in the routing proxy
- **Date:** 2026-11-09
- **Context:** FT-003. A decoy skips core banking and answers faster than the real service, which an attacker watching the screen can notice. Options were a latency learned from real calls, fixed ranges configured per method, or a common floor for real and decoy calls. Felipe chose the first.
- **Choice:** the routing proxy times every NORMAL call and records it per routed method and outcome (`RETURNED` or `THREW`) in the `LatencyProfile` SPI (core, in-memory ring buffer by default). A DURESS call runs the decoy first, then waits with a blocking sleep for the rest of a duration sampled from those observations, or from a configured fallback range while there are too few. The logic is `LatencyShaper` in core. Durations use a monotonic nanosecond source, not `Clock`.
- **Consequences:** real calls are never delayed. A request thread is held for the length of a real call during duress. Parity is per routed call, per instance, and does not correct a decoy that is slower than the real service. Profile failures are swallowed like alert failures.

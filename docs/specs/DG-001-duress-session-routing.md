# DG-001 Duress session routing

| Field | Value |
|---|---|
| Status | Implemented (v0.1.0-SNAPSHOT) |
| Owner | Felipe Martins |
| Modules | `decoy-guard-core`, `decoy-guard-spring-boot-starter`, `decoy-guard-demo` |
| Agent format | [DG-001-duress-session-routing.toon](DG-001-duress-session-routing.toon) |

## Context

A coerced user must be able to log in, see a believable account and "send" transfers without any real effect, while the bank is notified. Application code (controllers, use cases) should not need to know whether the session is real or a decoy.

## Goals

* Route each call to the real or the decoy implementation of a service, per session, transparently.
* Keep decoy implementations impossible to inject by mistake.
* Raise exactly one silent alert when a session enters duress mode.
* Keep every storage and alert component replaceable.

## Non-goals

* Risk signal detection on the device (future spec).
* Durable storage implementations (future spec).
* Response time shaping (future spec).

## Functional requirements

| Id | Priority | Requirement |
|---|---|---|
| FR-01 | Must | A session has a mode: `NORMAL` or `DURESS`. Unknown or anonymous sessions are `NORMAL`. |
| FR-02 | Must | `DuressActivator.activate(sessionId, trigger)` switches a session to `DURESS` and is idempotent; the first activation's trigger and time are kept. |
| FR-03 | Must | Each activation of a previously `NORMAL` session publishes exactly one `SilentAlert`. |
| FR-04 | Must | A bean annotated `@Decoy` stands in for the interfaces it implements, or only those listed in the annotation. |
| FR-05 | Must | Beans implementing a routed interface are wrapped so that, per call, `DURESS` sessions reach the decoy and `NORMAL` sessions reach the real bean. |
| FR-06 | Must | `@Decoy` beans are never autowire candidates, including when the starter is disabled. |
| FR-07 | Must | Two `@Decoy` beans for the same interface fail application startup. |
| FR-08 | Must | The session mode is resolved once per HTTP request and cleared at the end of it. |
| FR-09 | Should | Decoy transfers are recorded as `HeldTransaction`s with the destination typed by the user. |
| FR-10 | Should | `decoy-guard.enabled=false` disables routing, the filter and the default SPI beans. |

## Non-functional requirements

| Id | Requirement |
|---|---|
| NFR-01 | The mode is never written to any response (headers, body, cookies). |
| NFR-02 | A failing alert channel never fails the request that triggered it. |
| NFR-03 | `decoy-guard-core` has no runtime dependencies. |
| NFR-04 | Java 21, Spring Boot 4.1. |

## Design

* `DuressContext` (record) carries session id, mode, trigger and activation time. `DuressContextHolder` binds it to the current thread.
* `DuressContextFilter` (servlet filter, order `HIGHEST_PRECEDENCE + 100`) uses a `SessionIdExtractor` and the `DuressSessionRegistry` to resolve the context.
* `DecoyRoutingPostProcessor` is both a `BeanFactoryPostProcessor` (finds `@Decoy` beans, marks them non-autowirable, maps interface to decoy bean name) and a `BeanPostProcessor` (wraps real beans in a JDK proxy whose interceptor checks `DuressContextHolder` on every call).
* Routing proxies are interface based. Consumers must inject the interface, not the concrete class.

## Acceptance criteria

* AC-01: Logging in with the real PIN and reading the balance returns the real balance.
* AC-02: Logging in with the duress PIN and reading the balance returns the decoy balance.
* AC-03: A Pix in a duress session returns status `CONCLUIDO`, lowers the decoy balance, leaves the real balance unchanged and appears in the fraud desk view.
* AC-04: A Pix above the decoy balance fails with the same status and message as the real account.
* AC-05: Balance responses have the same fields in both modes.
* AC-06: With `decoy-guard.enabled=false`, the application starts and a duress context still reaches the real bean.
* AC-07: Two decoys for one interface make the context fail to start.

## Test coverage

| Criteria | Test |
|---|---|
| AC-01 to AC-05 | `DuressFlowTest` (demo) |
| AC-06, AC-07, FR-05, FR-06 | `DecoyRoutingTest` (starter) |
| FR-01 to FR-03, NFR-02 | `InMemoryDuressSessionRegistryTest`, `DuressActivatorTest` (core) |

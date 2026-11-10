# Plan: Duress session routing (FT-001)

| Field | Value |
|---|---|
| Feature id | FT-001 (legacy DG-001) |
| Status | Implemented (v0.1.0-SNAPSHOT) |
| Owner | Felipe Martins |
| Modules | `decoy-guard-core`, `decoy-guard-spring-boot-starter`, `decoy-guard-demo` |
| Feature spec | [duress-session-routing.spec.toon](../specs/features/duress-session-routing.spec.toon) |
| Acceptance | [duress-session-routing.acceptance.toon](../specs/acceptance/duress-session-routing.acceptance.toon) |

Migrated from `docs/specs/DG-001-duress-session-routing.md` and `.toon`. Requirement and criterion ids are kept. Each FR-NN is also business rule BR-0NN in the feature spec.

## Summary

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

| Id | Rule | Priority | Requirement |
|---|---|---|---|
| FR-01 | BR-001 | Must | A session has a mode: `NORMAL` or `DURESS`. Unknown or anonymous sessions are `NORMAL`. |
| FR-02 | BR-002 | Must | `DuressActivator.activate(sessionId, trigger)` switches a session to `DURESS` and is idempotent; the first activation's trigger and time are kept. |
| FR-03 | BR-003 | Must | Each activation of a previously `NORMAL` session publishes exactly one `SilentAlert`. |
| FR-04 | BR-004 | Must | A bean annotated `@Decoy` stands in for the interfaces it implements, or only those listed in the annotation. |
| FR-05 | BR-005 | Must | Beans implementing a routed interface are wrapped so that, per call, `DURESS` sessions reach the decoy and `NORMAL` sessions reach the real bean. |
| FR-06 | BR-006 | Must | `@Decoy` beans are never autowire candidates, including when the starter is disabled. |
| FR-07 | BR-007 | Must | Two `@Decoy` beans for the same interface fail application startup. |
| FR-08 | BR-008 | Must | The session mode is resolved once per HTTP request and cleared at the end of it. |
| FR-09 | BR-009 | Should | Decoy transfers are recorded as `HeldTransaction`s with the destination typed by the user. |
| FR-10 | BR-010 | Should | `decoy-guard.enabled=false` disables routing, the filter and the default SPI beans. |

## Non-functional requirements

| Id | Requirement |
|---|---|
| NFR-01 | The mode is never written to any response (headers, body, cookies). |
| NFR-02 | A failing alert channel never fails the request that triggered it. |
| NFR-03 | `decoy-guard-core` has no runtime dependencies. |
| NFR-04 | Java 21, Spring Boot 4.1. |

## Scope

* **Layers affected:** core model and SPIs, Spring adapter (starter), demo API.
* **New files:** see the class map in [project-reference.md](../project-reference.md#3-module-and-class-map).
* **Modified files:** none (initial implementation).

## Approach (design)

* `DuressContext` (record) carries session id, mode, trigger and activation time. `DuressContextHolder` binds it to the current thread.
* `DuressContextFilter` (servlet filter, order `HIGHEST_PRECEDENCE + 100`) uses a `SessionIdExtractor` and the `DuressSessionRegistry` to resolve the context.
* `DecoyRoutingPostProcessor` is both a `BeanFactoryPostProcessor` (finds `@Decoy` beans, marks them non-autowirable, maps interface to decoy bean name) and a `BeanPostProcessor` (wraps real beans in a JDK proxy whose interceptor checks `DuressContextHolder` on every call).
* Routing proxies are interface based. Consumers must inject the interface, not the concrete class.

### Components

| Component | Module | Role |
|---|---|---|
| `DuressContext` | core | Record with sessionId, mode, trigger, activatedAt |
| `DuressContextHolder` | core | ThreadLocal binding of the current context |
| `DuressActivator` | core | Activates the session and publishes one alert, swallowing alert failures |
| `DuressContextFilter` | starter | Servlet filter resolving the context per request |
| `DecoyRoutingPostProcessor` | starter | BFPP hides decoys; BPP wraps real beans in routing JDK proxies |
| `SessionIdExtractor` | starter | SPI; default reads the bearer token |
| `DecoyGuardAutoConfiguration` | starter | Wires defaults with `ConditionalOnMissingBean` |

### SPIs

| SPI | Default | Production hint |
|---|---|---|
| `DuressSessionRegistry` | `InMemoryDuressSessionRegistry` | Redis or database shared by all instances |
| `HeldTransactionStore` | `InMemoryHeldTransactionStore` | Durable store read by the fraud team |
| `SilentAlertPublisher` | `LoggingSilentAlertPublisher` | Fraud queue or trusted contact channel |
| `SessionIdExtractor` | `BearerTokenSessionIdExtractor` | JWT claim or session cookie |

### Rules

* Inject routed services by interface, never by concrete class.
* Real and decoy must share DTOs, exceptions and id formats.
* Never expose the session mode to the client.
* The decoy must keep working when the alert channel fails.

## Tasks

1. [x] Core model, `DuressContextHolder`, `DuressActivator`, SPIs and in-memory defaults
2. [x] Starter: `@Decoy`, `DecoyRoutingPostProcessor`, `DuressContextFilter`, auto-configuration
3. [x] Demo: real and decoy `AccountService`, login, fraud desk
4. [x] Tests for every acceptance criterion

## Database changes

None. Storage is behind SPIs with in-memory defaults.

## Risks and open questions

* Response time is not shaped yet, so decoy calls are faster than real ones (see `docs/threat-model.md`).
* The context is thread-bound. `@Async` and reactive code must propagate it explicitly.

## Acceptance criteria

* [x] AC-01: Logging in with the real PIN and reading the balance returns the real balance.
* [x] AC-02: Logging in with the duress PIN and reading the balance returns the decoy balance.
* [x] AC-03: A Pix in a duress session returns the same status as a real Pix (`EM_PROCESSAMENTO` since FT-004, `CONCLUIDO` before it), lowers the decoy balance, leaves the real balance unchanged and appears in the fraud desk view.
* [x] AC-04: A Pix above the decoy balance fails with the same status and message as the real account.
* [x] AC-05: Balance responses have the same fields in both modes.
* [x] AC-06: With `decoy-guard.enabled=false`, the application starts and a duress context still reaches the real bean.
* [x] AC-07: Two decoys for one interface make the context fail to start.

## Test coverage

| Criteria | Test |
|---|---|
| AC-01 to AC-05 | `DuressFlowTest` (demo) |
| AC-06, AC-07, FR-05, FR-06 | `DecoyRoutingTest` (starter) |
| FR-01 to FR-03, NFR-02 | `InMemoryDuressSessionRegistryTest`, `DuressActivatorTest` (core) |

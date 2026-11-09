# Architecture

## Overview
- **Style:** ports and adapters at module level. `decoy-guard-core` holds the model and the ports (SPIs). `decoy-guard-spring-boot-starter` is the Spring adapter. `decoy-guard-demo` is a sample application that consumes the starter.
- **Packaging strategy:** by module. Inside the demo, by feature (`account`, `auth`, `fraud`).
- **Base package:** `io.github.valossa515.decoyguard`

## Modules and Responsibilities

| Module | Package | Responsibility | Depends on |
|--------|---------|----------------|------------|
| `decoy-guard-core` | `...core` | Session model, duress activation, SPIs and in-memory defaults. Framework agnostic. | Nothing at runtime |
| `decoy-guard-spring-boot-starter` | `...spring` | `@Decoy`, routing post processor, request filter, auto-configuration, default extractor and alert publisher | core, Spring Boot autoconfigure, Spring AOP, Spring Web |
| `decoy-guard-demo` | `...demo` | Fake bank API: real and decoy account, login, fraud desk | starter, Spring Web MVC |

## SPIs

| SPI | Module | Default | Production hint |
|-----|--------|---------|-----------------|
| `DuressSessionRegistry` | core | `InMemoryDuressSessionRegistry` | Redis or database shared by all instances |
| `HeldTransactionStore` | core | `InMemoryHeldTransactionStore` | Durable store read by the fraud team |
| `SilentAlertPublisher` | core | `LoggingSilentAlertPublisher` (starter) | Fraud queue, trusted contact notification |
| `SessionIdExtractor` | starter | `BearerTokenSessionIdExtractor` | JWT claim, session cookie |
| `LatencyProfile` | core | `InMemoryLatencyProfile` | Store shared by all instances |

Every default is registered with `@ConditionalOnMissingBean`, so an application bean replaces it.

## Request Lifecycle

1. `DuressContextFilter` (order `HIGHEST_PRECEDENCE + 100`) asks the `SessionIdExtractor` for the session id and the `DuressSessionRegistry` for its context, then binds it with `DuressContextHolder.set`.
2. The controller calls a routed interface. The bean it holds is a JDK proxy created by `DecoyRoutingPostProcessor`.
3. The proxy interceptor checks `DuressContextHolder.isDuress()` on every call. NORMAL proceeds to the real bean. DURESS invokes the same method on the `@Decoy` bean.
4. The filter clears the holder in `finally`. Nothing is written to the response.

## Routing Mechanics (`DecoyRoutingPostProcessor`)

* As a `BeanFactoryPostProcessor`: finds beans annotated `@Decoy`, sets `autowireCandidate=false`, maps each routed interface to the decoy bean name, and fails if two decoys claim the same interface or a listed interface is not implemented.
* As a `BeanPostProcessor`: wraps every non-decoy bean that implements a routed interface in an interface-based proxy (`proxyTargetClass=false`). Consumers must inject the interface, never the concrete class.
* Always registered, even with `decoy-guard.enabled=false`. In that case decoys stay hidden but calls are never routed.

## Module and Class Map
Full class list with responsibilities: [project-reference.md](project-reference.md#3-module-and-class-map).

## Domain Boundaries
- **Session mode** (core): which sessions are DURESS, and the alert raised on activation.
- **Held transactions** (core): what was intercepted, for the fraud team.
- **Account** (demo): what the bank app screen shows, real or decoy.
- **Auth** (demo): PIN check and opaque session tokens.
- **Fraud desk** (demo): back office view. In a real deployment it lives in a separate, internally authenticated system.

## C4 Summary
- **Context:** coerced account holder and attacker in front of the bank app, the bank's backend embedding Decoy Guard, the fraud team, an optional trusted contact.
- **Containers:** the bank's Spring Boot backend with the starter. Future: shared session registry (Redis), durable held transaction store (JDBC), alert channel (queue).
- **Components:** filter, routing proxy, activator, SPIs, real and decoy service implementations.

## External Integrations

| System | Protocol | Purpose |
|--------|----------|---------|
| None yet | | Storage and alert delivery are SPIs with in-memory and logging defaults |

## Key Design Decisions
See [decisions.md](decisions.md).
- **Database:** none. State behind SPIs.
- **Routing:** per-call JDK proxy over interfaces, chosen by a ThreadLocal context.
- **Auth:** out of scope for the library. The demo uses opaque bearer tokens.
- **Messaging:** none yet. Alerts go through `SilentAlertPublisher`.

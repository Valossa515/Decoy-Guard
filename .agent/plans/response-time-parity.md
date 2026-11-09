# Plan: Response time parity (FT-003)

| Field | Value |
|---|---|
| Feature id | FT-003 |
| Status | Approved 2026-11-09 (latency source: option A), implemented |
| Contract | [response-time-parity.contract.toon](../specs/contracts/response-time-parity.contract.toon) and [architecture notes](response-time-parity-architecture.md) |
| Test plan | [response-time-parity-tests.md](response-time-parity-tests.md) |
| Owner | Felipe Martins |
| Modules | `decoy-guard-core`, `decoy-guard-spring-boot-starter`, `decoy-guard-demo` |
| Feature spec | [response-time-parity.spec.toon](../specs/features/response-time-parity.spec.toon) |
| Acceptance | [response-time-parity.acceptance.toon](../specs/acceptance/response-time-parity.acceptance.toon) |

## Summary

A decoy call skips core banking and the Pix rail, so it answers faster than a real call. Timing is observable by an attacker who watches the screen, and the threat model marks indistinguishability as "Partial: timing not shaped yet". This feature makes a call on a routed interface in a DURESS session take about as long as the same call takes in NORMAL sessions. The library observes how long real calls take and holds each decoy call until a duration drawn from those observations has passed.

## Goals

* A decoy call is not noticeably faster than the real call it stands in for.
* Real customers are never slowed down.
* The bank does not have to configure latency per method by hand: the library learns it from real traffic and has a configured fallback for cold start.
* The demo shows the effect, so the two phones respond at the same pace.

## Non-goals

* Shaping the latency of anything outside routed interface calls (login, static resources, network).
* Making a decoy faster when it is slower than the real call. The decoy author owns that.
* Sharing observations between application instances (comes with the Redis and JDBC item).
* Non-blocking delay for reactive pipelines or `@Async` (comes with the context propagation item).
* Resisting statistical timing analysis over thousands of calls. The attacker in scope watches a screen.

## Gap analysis

| # | Gap | Class | Resolution |
|---|---|---|---|
| 1 | Where the target latency comes from | Blocking | Resolved: option A, chosen by Felipe on 2026-11-09. |
| 2 | Granularity of the observations | Non-blocking | Per routed method and per outcome (returned or threw). A rejected Pix is usually faster than an accepted one, so mixing them would leak. |
| 3 | Cold start, before any real call was observed | Non-blocking | Configured fallback range, default 150 to 400 ms, used until a method and outcome has 20 observations. |
| 4 | The demo's real service is in memory and as fast as the decoy | Non-blocking | The demo's `RealAccountService` simulates core banking latency (configurable, default 120 to 350 ms), so there is something to match. |
| 5 | Decoy slower than the target | Non-blocking | No extra delay. Documented as a responsibility of the decoy author. |
| 6 | How the delay is applied (blocking sleep or other) | Non-blocking | Left to the architect. The spec only requires that the result and the exception are unchanged. |

### Options for gap 1

* **Option A, learned from real calls (chosen).** The routing proxy times every NORMAL call per method and outcome and keeps a bounded window of recent durations. A DURESS call is held until a duration sampled from that window has passed. Follows the real backend as it changes, needs no tuning, and does not slow real customers.
* **Option B, configured only.** The bank declares a latency range per method in properties. Simplest, but it goes stale as the backend changes and nobody remembers to update it.
* **Option C, common floor.** Both real and decoy calls are padded to a fixed minimum time. Strongest parity, but it slows every real customer, which a bank will not accept.

## Functional requirements

| Id | Rule | Priority | Requirement |
|---|---|---|---|
| FR-01 | BR-001 | Must | The duration of every call on a routed interface in a NORMAL session is observed, per routed method and per outcome (returned or threw). |
| FR-02 | BR-002 | Must | Observations are kept in a bounded window of the most recent ones per method and outcome (default 500). |
| FR-03 | BR-003 | Must | A call on a routed interface in a DURESS session does not complete before a target duration sampled from the observations of the same method and the same outcome. |
| FR-04 | BR-004 | Must | While a method and outcome has fewer than the minimum number of observations (default 20), the target is drawn from a configured fallback range (default 150 to 400 ms). |
| FR-05 | BR-005 | Must | Shaping never changes what the decoy returned or threw. An exception is rethrown unchanged after the wait. |
| FR-06 | BR-006 | Must | Calls in NORMAL sessions are never delayed. |
| FR-07 | BR-007 | Must | Calls in DURESS sessions are never recorded as observations. |
| FR-08 | BR-008 | Must | When the decoy itself took longer than the target, no delay is added. |
| FR-09 | BR-009 | Should | `decoy-guard.latency-shaping.enabled=false` turns shaping and observation off. `decoy-guard.enabled=false` turns them off too. |
| FR-10 | BR-010 | Should | The demo's real account simulates core banking latency, so both phones respond at the same pace. |

## Non-functional requirements

| Id | Requirement |
|---|---|
| NFR-01 | Shaping adds no header, field, cookie or client-visible log line. The mode stays unobservable (rule 1). |
| NFR-02 | `decoy-guard-core` keeps zero runtime dependencies. The observation store is an SPI in core with an in-memory default (rule 5). |
| NFR-03 | Observation overhead on a NORMAL call is negligible (two clock reads and one bounded write). |
| NFR-04 | A failure while observing or sampling never fails the request. The call proceeds without shaping. |
| NFR-05 | Tests do not sleep for real in core and starter: time and waiting are injected. |

## Scope

* **Layers affected:** core (new SPI and in-memory default), starter (routing proxy, auto-configuration, properties), demo (simulated latency in the real service, tests), docs.
* **New files:** `CallOutcome`, `LatencyProfile`, `InMemoryLatencyProfile`, `Sleeper` and `LatencyShaper` in core, `LatencyShapingProperties` in the starter, `CoreBankingLatency` in the demo, tests in the three modules, `.agent/plans/response-time-parity-architecture.md`, `.agent/plans/response-time-parity-tests.md`, `.agent/specs/contracts/response-time-parity.contract.toon`.
* **Modified files:** `DecoyRoutingPostProcessor`, `DecoyGuardAutoConfiguration`, `RealAccountService`, demo `application.yml`, `README.md` (roadmap and configuration), `docs/threat-model.md` (status of indistinguishability and the response time limitation), `.agent/project-reference.md`, `.agent/decisions.md` (new ADR).

## Approach

Described at the level of behavior. The class design is in the [architecture notes](response-time-parity-architecture.md).

* The routing proxy already sees every call on a routed interface. It is the single place where the duration can be measured (NORMAL) and where the wait can be applied (DURESS).
* Observations live behind a core SPI, so a bank can later back them with shared storage.
* Time and waiting are injected, so tests use a fake clock and a fake sleeper.

## Tasks

1. [x] Architect: contract and architecture notes, checked against the threat model and the five rules. Stop for approval.
2. [x] Core: latency SPI and in-memory default with a bounded window.
3. [x] Starter: observe NORMAL calls and shape DURESS calls in the routing proxy, properties, auto-configuration.
4. [x] Demo: simulated core banking latency in `RealAccountService`.
5. [x] Test plan and tests referencing AC-01 to AC-11.
6. [x] Docs: README roadmap and configuration, threat model status, project reference, ADR.
7. [x] Review against the five rules, `mvn verify`. Commit after Felipe's approval.

## Database changes

None.

## Risks and open questions

1. **Latency source (gap 1).** Decided: option A.
2. **Blocked threads.** Holding a decoy call occupies a request thread for the wait. Duress sessions are rare, so the cost is small. The architect decides how the wait is done.
3. **Flaky timing tests.** The demo test compares real elapsed time. It must use wide tolerances and small simulated latencies, and the exact behavior is proven in the starter with a fake sleeper.
4. **Whole request versus routed call.** Parity is per routed call. A controller that makes a different number of routed calls per mode would still differ. Not the case in the demo, noted in the docs.
5. **Sampling gives a distribution, not a guarantee.** A decoy call can be faster or slower than one specific real call, just like two real calls differ. That is the intended behavior.

## Acceptance criteria

* [x] AC-01: NORMAL calls are not delayed.
* [x] AC-02: A DURESS call waits until the target sampled from the observations of the same method.
* [x] AC-03: Before the minimum number of observations, the target comes from the fallback range.
* [x] AC-04: A decoy exception is delayed with the observations of failed real calls and rethrown unchanged.
* [x] AC-05: DURESS calls do not feed the observations.
* [x] AC-06: A decoy slower than the target gets no extra delay.
* [x] AC-07: The observation window is bounded and keeps the most recent durations.
* [x] AC-08: With shaping disabled there is no delay and no observation.
* [x] AC-09: A failure in the latency store does not fail the call.
* [x] AC-10: In the demo, balance and Pix take a comparable time in both sessions, with the same response shape and headers.
* [x] AC-11: A Pix above the balance is rejected the same way and at a comparable pace in both sessions.

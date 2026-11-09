# Architecture: Response time parity (FT-003)

| Field | Value |
|---|---|
| Feature spec | [response-time-parity.spec.toon](../specs/features/response-time-parity.spec.toon) |
| Plan | [response-time-parity.md](response-time-parity.md) |
| Contract | [response-time-parity.contract.toon](../specs/contracts/response-time-parity.contract.toon) |
| Status | Approved 2026-11-09, implemented |

## Summary

The routing proxy in the starter is the only place that sees every call on a routed interface, so it is where real calls are timed and decoy calls are held. The logic itself lives in core, in a framework-agnostic `LatencyShaper`, next to a new storage SPI `LatencyProfile` with an in-memory default. The starter only wires them and delegates from the proxy. The decoy call runs first, then the shaper samples a target for the outcome the decoy produced and waits for the remaining time.

## Pattern analysis

* **Existing patterns found:**
  * `DuressActivator` (core): framework-agnostic logic over SPIs, swallows and logs SPI failures with `System.Logger`.
  * `HeldTransactionStore` and `InMemoryHeldTransactionStore`, `DuressSessionRegistry` and `InMemoryDuressSessionRegistry` (core): SPI plus single-instance default built on `ConcurrentHashMap`.
  * `DecoyGuardAutoConfiguration` (starter): nested `@Configuration` classes behind `@ConditionalOnProperty`, every SPI bean with `@ConditionalOnMissingBean`.
  * `InMemoryDuressSessionRegistry` takes a `Clock` in the constructor so tests control time.
* **Pattern followed:** SPI in core with an in-memory default, logic class in core, wiring in the starter. No new pattern.

## Design

### Core (`io.github.valossa515.decoyguard.core`)

| Type | Kind | Responsibility |
|---|---|---|
| `CallOutcome` | enum | `RETURNED` or `THREW`. |
| `LatencyProfile` | SPI | `record(String operation, CallOutcome outcome, Duration elapsed)` and `Optional<Duration> sample(String operation, CallOutcome outcome)`. `sample` is empty while there are too few observations. |
| `InMemoryLatencyProfile` | default | One fixed-size ring buffer per operation and outcome in a `ConcurrentHashMap`. Constructor takes window size, minimum observations and a `RandomGenerator`. `sample` returns one random element of the window. |
| `Sleeper` | functional interface | `void sleep(Duration)`, throws `InterruptedException`. Production value is `Thread::sleep`. |
| `LatencyShaper` | class | `Object observe(String operation, Call call)` times a real call and records it. `Object shape(String operation, Call call)` runs the decoy call, picks the target and waits for the remainder. Both rethrow whatever the call threw. |
| `LatencyShaper.Call` | functional interface | `Object proceed() throws Throwable`. |

`LatencyShaper` rules:

* Elapsed time comes from an injected `LongSupplier` of nanoseconds (`System::nanoTime` in production). `Clock` is wall time and can jump, so it is not used for durations.
* `shape` runs the decoy, measures it, then asks `LatencyProfile.sample(operation, outcome)`. If empty, the target is drawn uniformly from the fallback range. It sleeps `target - elapsed` when positive, then returns the result or rethrows the same exception.
* Any `RuntimeException` from the profile is caught and logged at `WARNING` with `System.Logger`. On `observe` the call result is returned as is. On `shape` the fallback range is used.
* If the wait is interrupted, the interrupt flag is restored and the result is returned at once.
* The operation key is the routed interface name, the method name and the parameter type names (`io...AccountService#pix(io...PixRequest)`), built by the starter. It is a plain string so a future shared store can use it as a key.

### Starter (`io.github.valossa515.decoyguard.spring`)

| Type | Change |
|---|---|
| `LatencyShapingProperties` | New `@ConfigurationProperties("decoy-guard.latency-shaping")`: `enabled` (true), `windowSize` (500), `minObservations` (20), `fallbackMin` (150ms), `fallbackMax` (400ms). Invalid values fail startup with `IllegalArgumentException`. |
| `DecoyGuardAutoConfiguration` | New nested `LatencyConfiguration`, active when `decoy-guard.enabled` and `decoy-guard.latency-shaping.enabled` are true. Beans: `LatencyProfile` (`@ConditionalOnMissingBean`, in-memory) and `LatencyShaper`. |
| `DecoyRoutingPostProcessor` | The interceptor looks up the `LatencyShaper` lazily from the bean factory on first use (the post processor is created before regular beans, so it cannot receive it in the constructor). With a shaper: NORMAL goes through `observe`, DURESS through `shape`. Without one, behavior is exactly today's. |

The spec input `decoy-guard.latency-shaping.fallback` becomes two properties, `fallback-min` and `fallback-max`, because Spring binds `Duration` values and has no range type.

### Demo

| Type | Change |
|---|---|
| `CoreBankingLatency` | New demo component. Sleeps a random time inside `demo.core-banking-latency.min` and `max` (120ms and 350ms). Stands in for the network hop to core banking. |
| `RealAccountService` | Calls `CoreBankingLatency` once per method, outside the `synchronized` block. An accepted Pix pays a second hop (the Pix rail). A rejected Pix pays only one, which makes the per-outcome observation visible in the demo. |
| `application.yml` | Documents the demo latency properties. |

`DecoyAccountService`, the DTOs, the controllers and the front end do not change. The GIF does not need to be recorded again.

## Layer decisions

| Layer | Decision | Justification |
|---|---|---|
| Core | SPI, in-memory default and `LatencyShaper` | Rule 5 and ADR-005. The logic stays testable without Spring, like `DuressActivator`. |
| Starter | Properties, wiring and delegation from the proxy | The proxy is the single choke point of routed calls (ADR-002). |
| Demo | Simulated latency in the real service | Without it both paths answer in under a millisecond and there is nothing to match. |

## Dependencies

* **Reusing:** `DuressContextHolder.isDuress()`, `DecoyRoutingPostProcessor`, `DecoyGuardAutoConfiguration`, `System.Logger` in core.
* **New:** none. `java.util.random.RandomGenerator` is part of the JDK. Core keeps zero runtime dependencies.

## Database changes

None.

## Check against the threat model and the five rules

| Rule or goal | Result |
|---|---|
| 1. Mode never observable | Nothing is written to the response. The only log line is a server-side warning on a profile failure and it does not carry the mode. The wait itself is what closes the timing gap. |
| 2. Real and decoy share DTOs, exceptions, ids | No routed interface changes. The decoy exception is rethrown unchanged. |
| 3. `@Decoy` beans never injectable | Untouched. The shaper lookup uses the bean factory for `LatencyShaper` only. |
| 4. Alert failures never fail the request | Untouched. The same principle is applied to the profile: its failures are swallowed. |
| 5. New storage is an SPI | `LatencyProfile` in core with `InMemoryLatencyProfile`. |
| Fail safe | A broken profile degrades to the fallback range. A decoy is never left unshaped while shaping is enabled. |
| Indistinguishability | After this feature the threat model status can move from "Partial: timing not shaped yet" to shaped per routed call, with the remaining limits listed below. |

## Test strategy rationale

* **Core unit tests** carry the exact timing behavior with a fake nano ticker, a recording `Sleeper` and a fixed `RandomGenerator`. No real sleeping.
* **Starter tests** with `ApplicationContextRunner` prove wiring: NORMAL is observed, DURESS is shaped, the property switches it off, a user `LatencyProfile` bean replaces the default, a throwing profile does not fail the call. They replace `Sleeper` and the ticker with test beans.
* **Demo MockMvc tests** prove the end-to-end effect with small simulated latencies and wide tolerances. The existing demo tests run with the simulated latency and the fallback set to zero in the test properties, so the suite does not get slower.
* **Excluded:** `LatencyShapingProperties` getters and `CallOutcome` (no logic beyond validation, which the starter test covers).

## Trade-offs and alternatives considered

* **Shaping in a servlet filter (whole request).** Rejected. It would also cover controller time, but it cannot tell which routed method ran or whether it threw, and it would not help non-HTTP callers of routed services.
* **Shaper logic inside the starter.** Rejected. Core keeps the logic so that a non-Spring integration can reuse it, as with `DuressActivator`.
* **Histogram or percentile model instead of a window of raw samples.** Rejected for now. Sampling a real past duration reproduces the real distribution, including its tail, with far less code. 500 longs per operation and outcome is small.
* **Non-blocking wait (scheduler and async response).** Rejected for now. Routed services are synchronous interfaces, so the caller has to block anyway. Duress sessions are rare, and applications on virtual threads pay nothing.
* **Sample the target before running the decoy.** Rejected. The outcome is only known after the decoy runs, and the outcome selects the bucket.

## Risks

* **Blocked request thread during the wait.** Bounded by the real latency of the same method. Documented in the README.
* **Slow decoy.** If a bank writes a decoy slower than the real call, parity breaks in the other direction. Documented as the decoy author's responsibility.
* **Calls per request.** Parity is per routed call. A controller that calls routed services a different number of times per mode is not covered.
* **Cold start after a deploy.** Until 20 real calls per method and outcome are seen, the fallback range is used. It should be tuned to the bank's typical latency.
* **Timing tests in CI.** Only the demo tests use real time, with tolerances wide enough for a slow runner.

## Decision recorded

[ADR-010](../decisions.md): decoy latency is learned from real calls and applied in the routing proxy with a blocking wait. Observations are kept per routed method and outcome behind the `LatencyProfile` SPI.

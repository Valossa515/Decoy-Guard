# Test plan: Response time parity (FT-003)

Acceptance criteria: [response-time-parity.acceptance.toon](../specs/acceptance/response-time-parity.acceptance.toon). Contract: [response-time-parity.contract.toon](../specs/contracts/response-time-parity.contract.toon).

## Strategy

Timing tests are flaky when they measure real time, so the exact behavior is proven with fake time and only the end-to-end effect uses a real clock.

* **Core** (`LatencyShaperTest`, `InMemoryLatencyProfileTest`): plain unit tests. Time is a counter, the `Sleeper` records the requested wait and moves the counter, randomness is fixed. Nothing sleeps.
* **Starter** (`LatencyShapingTest`): `ApplicationContextRunner` with the auto-configuration and a routed `Teller` interface. A test `LatencyShaper` bean uses the same fake time, and a recording `LatencyProfile` bean replaces the default. This proves the proxy delegates to `observe` and `shape` and that the properties wire and switch the feature.
* **Demo** (`ResponseTimeParityTest`): `@SpringBootTest` with MockMvc and real time. The simulated hop is fixed at 60 ms and the fallback range is zero, so a decoy call can only be slow if it learned that from real calls. Lower bounds are safe because a sleep never returns early. Upper bounds have 250 ms of slack for a slow CI runner.
* **Existing demo tests** (`DuressFlowTest`, `FrontEndTest`) set the simulated latency and the fallback range to zero, so they stay fast and keep covering functional parity.

No routed interface gained a method, so no new functional parity test is needed. `ResponseTimeParityTest` adds parity of response headers and of the rejected Pix body.

## Automated

| Test | Module | Criteria |
|---|---|---|
| `LatencyShapingTest.normalCallIsObservedAndNotDelayed` | starter | AC-01 |
| `LatencyShaperTest.observeRecordsTheDurationAndAddsNoDelay`, `observeRecordsFailedCallsSeparatelyAndRethrows` | core | AC-01 |
| `LatencyShapingTest.duressCallWaitsForTheSampledTarget` | starter | AC-02 |
| `LatencyShaperTest.shapeWaitsForTheRemainderOfTheSampledTarget` | core | AC-02 |
| `LatencyShapingTest.duressCallUsesTheFallbackRangeBeforeEnoughObservations` | starter | AC-03 |
| `LatencyShaperTest.shapeUsesTheFallbackRangeWhileTheProfileIsEmpty`, `InMemoryLatencyProfileTest.emptyBelowTheMinimumNumberOfObservations` | core | AC-03 |
| `LatencyShapingTest.decoyExceptionIsDelayedLikeAFailedRealCallAndRethrownUnchanged` | starter | AC-04 |
| `LatencyShaperTest.shapeDelaysAFailureLikeAFailedRealCallAndRethrowsIt` | core | AC-04 |
| `LatencyShapingTest.duressCallsAreNotObserved`, `LatencyShaperTest.shapeNeverRecords` | starter, core | AC-05 |
| `LatencyShapingTest.slowDecoyGetsNoExtraDelay`, `LatencyShaperTest.slowDecoyGetsNoExtraDelay` | starter, core | AC-06 |
| `InMemoryLatencyProfileTest.windowIsBoundedAndKeepsTheMostRecent`, `operationsAndOutcomesAreIndependent` | core | AC-07 |
| `LatencyShapingTest.shapingDisabledByPropertyKeepsRouting`, `starterDisabledRegistersNoLatencyBeans` | starter | AC-08 |
| `LatencyShapingTest.latencyProfileFailureDoesNotFailTheCall`, `LatencyShaperTest.failingProfileNeverFailsTheCall` | starter, core | AC-09 |
| `ResponseTimeParityTest.balanceAndPixTakeComparableTimeInBothSessions`, `responsesHaveTheSameFieldsAndHeaders` | demo | AC-10 |
| `ResponseTimeParityTest.rejectedPixIsIdenticalAndComparableInBothSessions` | demo | AC-11 |

Also covered, without a criterion of their own: `LatencyShapingTest.defaultsAreRegisteredAndBackOffForUserBeans`, `invalidPropertiesFailStartup` (startup failure `INVALID_LATENCY_CONFIG`), `LatencyShaperTest.interruptedWaitRestoresTheFlagAndReturnsTheResult`, `rejectsAnInvalidFallbackRange`, `InMemoryLatencyProfileTest.neverSamplesFromAnEmptyWindow`, `rejectsInvalidSizes`.

## Check that the demo test can fail

Running `ResponseTimeParityTest` with `decoy-guard.latency-shaping.enabled=false` makes `balanceAndPixTakeComparableTimeInBothSessions` and `rejectedPixIsIdenticalAndComparableInBothSessions` fail, because the decoy then answers in about a millisecond. Checked on 2026-11-09.

## Manual

| Check | Criteria |
|---|---|
| Run the demo, use both phones on the web page and confirm that balance, statement and Pix answer at the same pace in both | AC-10, BR-010 |

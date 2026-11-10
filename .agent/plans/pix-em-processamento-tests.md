# Test plan: Pix em processamento (FT-004)

Acceptance criteria: [pix-em-processamento.acceptance.toon](../specs/acceptance/pix-em-processamento.acceptance.toon). Contract: [pix-em-processamento.contract.toon](../specs/contracts/pix-em-processamento.contract.toon).

## Strategy

The feature lives in the demo, so it is tested end to end through HTTP with `@SpringBootTest` and MockMvc, like the other demo tests. There are no unit tests of the services alone.

* **`PixProcessingTest`** (new) covers AC-01 to AC-09. The simulated core banking latency and the fallback range are zero, so it is fast. The settlement time is 30 seconds.
* **Time** is a `MutableClock` fake, declared as a `@Primary` bean in a nested `@TestConfiguration`. A test moves it forward, so the real Pix settles without any waiting. The clock only moves forward and every test sends its own Pix, so the tests do not depend on each other.
* **Parity of the new routed method** (`findPix`): the tests compare the body, the field names and the header names of the consultation between a normal and a duress session, for the found case and for the 404.
* **`FrontEndTest`** gets one test for AC-10. The FT-002 test that lists the paths used by the phone code now also expects `/account/pix/`.
* **`DuressFlowTest`** expects `EM_PROCESSAMENTO` on the held Pix instead of `CONCLUIDO`.
* Balances are compared as differences, because the real account is shared by every test in the same context.

## Automated

| Test | Module | Criteria |
|---|---|---|
| `PixProcessingTest.normalPixStartsInProcessing` | demo | AC-01 |
| `PixProcessingTest.duressPixStartsInProcessingWithTheSameFieldsAndHeaders` | demo | AC-02 |
| `PixProcessingTest.realPixSettlesAfterTheSettlementTime` | demo | AC-03 |
| `PixProcessingTest.heldPixStaysInProcessingAfterTheSettlementTime` | demo | AC-04 |
| `PixProcessingTest.consultationMatchesTheOriginalReceiptInBothSessions` | demo | AC-05 |
| `PixProcessingTest.unknownAndOtherModeIdsReturnTheSame404InBothSessions` | demo | AC-06 |
| `PixProcessingTest.balanceDropsWhileThePixIsInProcessing` | demo | AC-07 |
| `PixProcessingTest.heldPixIsListedByTheFraudDeskAndRealBalanceIsUnchanged` | demo | AC-08 |
| `PixProcessingTest.consultationWithoutASessionReturns401` | demo | AC-09 |
| `PixProcessingTest.negativeSettlementIsRejected` | demo | BR-009 |
| `FrontEndTest.receiptFollowsThePixStatusFromThePhoneCode` | demo | AC-10 |
| `DuressFlowTest.duressPixLooksSuccessfulButIsHeld` (updated) | demo | FT-001 AC-03 |

## Edge cases covered

* The real Pix is still in processing one second before the settlement time and concluded exactly at it.
* The held Pix is still in processing a day after the settlement time.
* A session that asks for the Pix of the other mode gets the same 404 as for an id that does not exist.
* A consultation with a token that is not an open session gets 401, like one with no token.

## Check that the tests can fail

With the decoy answering `CONCLUIDO` and the real Pix never settling, six tests fail: `duressPixStartsInProcessingWithTheSameFieldsAndHeaders`, `realPixSettlesAfterTheSettlementTime`, `heldPixStaysInProcessingAfterTheSettlementTime`, `balanceDropsWhileThePixIsInProcessing`, `heldPixIsListedByTheFraudDeskAndRealBalanceIsUnchanged` and `DuressFlowTest.duressPixLooksSuccessfulButIsHeld`. Checked on 2026-11-10, then the code was restored.

## Not automated

| Check | Criteria |
|---|---|
| Run the demo, send a Pix on both phones and watch the receipt: both start as "Em processamento", the normal phone changes to "Concluído" after about 5 seconds and the duress phone does not. Leaving the receipt stops the consultations. | AC-10, BR-010 |

The build has no browser test, so the polling in `app.js` is only checked by this manual step. Done on 2026-11-10 with the default settlement of 5 seconds: both receipts showed "Em processamento" at first and after 4 seconds, the normal one showed "Concluído" after 10 seconds, the duress one did not, and no consultation was made after leaving the receipt. The retry after a failed consultation was not exercised.

## Infrastructure

JUnit 5, AssertJ, MockMvc and JsonPath, all already in the project. No Mockito and no new dependency.

# Architecture: Pix em processamento (FT-004)

| Field | Value |
|---|---|
| Feature spec | [pix-em-processamento.spec.toon](../specs/features/pix-em-processamento.spec.toon) |
| Plan | [pix-em-processamento.md](pix-em-processamento.md) |
| Contract | [pix-em-processamento.contract.toon](../specs/contracts/pix-em-processamento.contract.toon) |
| Status | Approved 2026-11-10, implemented |

## Summary

The whole feature lives in the demo. The routed interface `AccountService` gets one more method, `findPix(endToEndId)`, which returns the existing `PixReceipt`. The status becomes a shared enum, `PixStatus`, with the same JSON as today. The real service remembers the receipts it issued and derives the status on each read from the injected `Clock` and a settlement time. The decoy rebuilds the receipt from the held transactions of the current session and always answers `EM_PROCESSAMENTO`. Nothing is scheduled and nothing is stored outside what already exists. The library does not change: routing and response time shaping (FT-003) pick up the new method by themselves.

## Pattern analysis

* **Existing patterns found:**
  * `AccountService`: routed interface with the DTOs as nested records, shared by real and decoy (ADR-007).
  * `InsufficientFundsException`: one exception thrown by both implementations, mapped by an `@ExceptionHandler` in `AccountController` to a small JSON map.
  * `RealAccountService`: in-memory state of the fake bank (`available`, `entries`) guarded by `synchronized`, one `CoreBankingLatency.hop()` per call outside the lock.
  * `DecoyAccountService`: every answer derived from `HeldTransactionStore.findBySession` and the current `DuressContext`. No state of its own.
  * `CoreBankingLatency`: demo property read with `@Value` in the constructor and validated there.
* **Pattern followed:** all of the above. No new pattern.

## Design

### Routed interface (`demo.account.AccountService`)

| Type | Change |
|---|---|
| `PixStatus` | New nested enum: `EM_PROCESSAMENTO`, `CONCLUIDO`. |
| `PixReceipt` | `status` changes from `String` to `PixStatus`. The JSON is the same string as before, so the response shape does not change. |
| `findPix(String endToEndId)` | New method. Returns the `PixReceipt` of a Pix of this account with its current status. Throws `PixNotFoundException` when the account has no Pix with that id. |

### Shared exception

`PixNotFoundException` (`demo.account`), message "Pix não encontrado", thrown by both implementations. `AccountController` maps it to 404 with `{"error":"Pix não encontrado"}`, next to the existing handler.

### Real service (`RealAccountService`)

* Keeps the receipts it issued in a map by end to end id, under the same lock as the balance.
* `pix` returns the receipt with `EM_PROCESSAMENTO` and stores it.
* `findPix` makes one simulated hop, like `balance` and `statement`, then looks the receipt up. The status is `CONCLUIDO` when `clock.instant()` is not before `receipt.at()` plus the settlement time, otherwise `EM_PROCESSAMENTO`. An unknown id pays the same hop and then throws.
* The settlement time is `demo.pix-settlement` (default 5s), read with `@Value` in the constructor. A negative value fails startup.
* The status is derived on read. There is no scheduler and no thread that flips it.

### Decoy service (`DecoyAccountService`)

* `pix` returns `EM_PROCESSAMENTO`. It reads the clock once and uses that instant for both the held transaction and the receipt. Today it reads the clock twice, so the two instants can differ, and a consulted receipt would not match the original one (BR-005).
* `findPix` searches the held transactions of the current session for the id and rebuilds the receipt from it: id, counterparty as key, amount, `heldAt` as time, status `EM_PROCESSAMENTO`. Not found throws `PixNotFoundException`.
* It does not read the settlement time. A held Pix never settles.

### Controller (`AccountController`)

* `GET /account/pix/{endToEndId}` delegates to `accounts.findPix`. `SessionInterceptor` already covers `/account/**`, so it returns 401 without a session.
* New `@ExceptionHandler(PixNotFoundException.class)` with `HttpStatus.NOT_FOUND`.

### Front end (`static/app.js`, `styles.css`)

* `STATUS_LABELS` gains `EM_PROCESSAMENTO: 'Em processamento'`.
* One function renders the receipt from a `PixReceipt`. It is used for the answer of the POST and for every consultation. It also toggles a `processing` class on the receipt screen and switches the icon, so the screen does not show a green check while the Pix is in processing.
* While the receipt screen is open and the status is not `CONCLUIDO`, the phone consults `GET /account/pix/{id}` every 2 seconds. The timer is cleared when the phone leaves the receipt screen, logs out or gets a final status. A consultation that fails is tried again 2 seconds later, unless the session ended or the answer is 404.
* All of it is inside `mountPhone`, so both phones run the same code. `index.html` changes only if the icon needs a hook, and the phone template gets no mode marker.

### Configuration

`demo.pix-settlement: 5s` documented in the demo `application.yml`.

## Layer decisions

| Layer | Decision | Justification |
|---|---|---|
| Core | No change | `HeldTransactionStore.findBySession` is enough for the decoy. |
| Starter | No change | The routing proxy and the latency shaper work per method of the routed interface. |
| Demo | Interface method, enum, exception, both implementations, controller, property, front end | The spec is about what the fake bank shows. |

## Dependencies

* **Reusing:** `HeldTransactionStore`, `DuressContextHolder`, `Clock`, `CoreBankingLatency`, `EndToEndIds`, `SessionInterceptor`.
* **New:** none.

## Database changes

None.

## Check against the threat model and the five rules

| Rule or goal | Result |
|---|---|
| 1. Mode never observable | The POST answers `EM_PROCESSAMENTO` in both modes. The consultation has the same fields, the same 404 and the same 401 in both modes. No header, cookie or log line is added. |
| 2. Real and decoy share DTOs, exceptions, ids | One `PixReceipt`, one `PixStatus`, one `PixNotFoundException`. `findPix` is implemented in both and gets a parity test. |
| 3. `@Decoy` beans never injectable | Untouched. The controller keeps injecting `AccountService`. |
| 4. Alert failures never fail the request | Untouched. |
| 5. New storage is an SPI | No new library storage. The receipts kept by `RealAccountService` are state of the fake core banking, like its balance and statement, not something a bank would plug in. The decoy uses the existing SPI. |
| Plausibility | The held Pix now has a reason for not arriving, which is the mitigation the threat model suggests for the receiving side. |
| Response time | `findPix` is a routed call, so the decoy is held like a real consultation, per outcome. Until 20 real consultations are seen it uses the fallback range, as with any method. |

What stays observable, to be written in the threat model: a real Pix settles in seconds and a held Pix never does. Someone who knows how the product works could read that as a sign. That attacker is out of scope, and the status buys time instead of making the deception permanent.

## Test strategy rationale

* **Demo MockMvc tests** in a new `PixProcessingTest` cover AC-01 to AC-09 end to end, with the simulated latency and the fallback range at zero like `DuressFlowTest`.
* **Time:** the test replaces the `Clock` with a small mutable fake declared as a `@Primary` bean in a `@TestConfiguration`. It advances the fake past the settlement time, so one test shows the real Pix going from `EM_PROCESSAMENTO` to `CONCLUIDO` and the held Pix staying. No sleeping and no Mockito.
* **Parity:** the fields and headers of the consultation and of the 404 are compared between a normal and a duress session.
* **Front end:** `FrontEndTest` gets one test for AC-10 (label, consultation path inside the phone code, template without mode markers).
* **Existing tests:** `DuressFlowTest` expects `CONCLUIDO` in the duress Pix and changes to `EM_PROCESSAMENTO`.
* **Excluded:** `PixStatus` and `PixNotFoundException` (no logic). No unit test of the services alone, the project tests the demo through HTTP. The one exception is the constructor check of a negative settlement time.

## Trade-offs and alternatives considered

* **Keep `status` as a `String` with constants.** Rejected. The enum gives the same JSON and stops the two implementations from drifting apart by a typo.
* **A separate status DTO for the consultation.** Rejected. Returning the same `PixReceipt` keeps one shape and lets the front end render both answers with one function.
* **A scheduler that settles the real Pix.** Rejected. Deriving the status from the clock on read is simpler, has no thread and is trivial to test.
* **A new store or SPI for the status of a held Pix.** Rejected. The held Pix never changes status, so there is nothing to store.
* **`findById` on `HeldTransactionStore`.** Rejected for now. It would change a core SPI for a demo need. Filtering the few held transactions of one session is enough.
* **Returning 404 for a malformed id through validation.** Rejected. Any id the account does not have is simply not found, which keeps one error path in both modes.

## Risks

* **Mutable clock in the test context.** A second `Clock` bean marked `@Primary` changes the clock for the whole test context. It is limited to `PixProcessingTest`.
* **Receipts kept forever in the real service.** The map grows with every Pix. It is a demo with a restart as its only lifecycle, like the statement list.
* **Polling.** One request every 2 seconds per open receipt. It stops on a final status, on leaving the screen, when the session ends and on a 404, so a closed session does not keep polling. Other failures are tried again.
* **README GIF.** The receipt in `docs/media/demo.gif` shows "Concluído". Recording it again is not in the contract until Felipe decides.

## Decision recorded

[ADR-013](../decisions.md): every Pix starts in processing in both modes, the real one settles after a configured time derived from the clock on read, the held one never settles, and the status is consulted through a new method of the routed interface.

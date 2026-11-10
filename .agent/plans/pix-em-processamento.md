# Plan: Pix em processamento (FT-004)

| Field | Value |
|---|---|
| Feature id | FT-004 |
| Status | Approved 2026-11-10, implemented |
| Contract | [pix-em-processamento.contract.toon](../specs/contracts/pix-em-processamento.contract.toon) and [architecture notes](pix-em-processamento-architecture.md) |
| Test plan | [pix-em-processamento-tests.md](pix-em-processamento-tests.md) |
| Owner | Felipe Martins |
| Modules | `decoy-guard-demo` (docs in `README.md` and `docs/threat-model.md`) |
| Branch | `feature/ft-004-pix-em-processamento` |
| Feature spec | [pix-em-processamento.spec.toon](../specs/features/pix-em-processamento.spec.toon) |
| Acceptance | [pix-em-processamento.acceptance.toon](../specs/acceptance/pix-em-processamento.acceptance.toon) |

## Summary

The threat model names the receiving side as the weak spot: a held Pix shows "Concluído" on the victim's screen and never arrives, so an attacker with a partner watching the destination account notices. This feature makes every Pix receipt start as "Em processamento" in both sessions. In a NORMAL session the Pix settles a few seconds later and becomes "Concluído". In a DURESS session the held Pix stays "Em processamento", which mirrors a real Pix stalled in anti-fraud review and explains why the money has not arrived. The app gets a way to consult a Pix after the receipt, so the screen follows the status.

## Goals

* A held Pix has a believable reason for not arriving at the destination.
* The first receipt is identical in both sessions: same fields, same status.
* The status of a Pix can be consulted later through the same routed interface in both sessions.
* The demo front end shows the status and follows it, with the same code on both phones.

## Non-goals

* A held Pix that later concludes or is returned. It stays in processing (decided by Felipe on 2026-11-10).
* A real Pix that stalls or is refused by anti-fraud.
* Showing the status in the statement. The statement stays as it is.
* A list of the Pix sent by the session, or pagination.
* Changes to the library (`decoy-guard-core`, starter). If the architect finds one is needed, it comes back for approval.
* Changes to the fraud desk. It keeps showing the held transaction.

## Gap analysis

| # | Gap | Class | Resolution |
|---|---|---|---|
| 1 | When a Pix shows "em processamento", and whether the real service produces it too | Blocking | Resolved by Felipe on 2026-11-10: every Pix, in both modes. A status that only the decoy produces would reveal the mode. |
| 2 | What happens to the held Pix afterwards | Blocking | Resolved: it stays in processing. |
| 3 | Where the client sees the status after the receipt | Blocking | Resolved: a new Pix consultation on the routed interface, `GET /account/pix/{endToEndId}`. |
| 4 | Balance while the Pix is in processing | Non-blocking | Debited at once in both modes, as today (plausibility). |
| 5 | How long a real Pix takes to settle | Non-blocking | Configurable in the demo, default 5 seconds, measured with the injected `Clock`. |
| 6 | Consulting an id the session does not have | Non-blocking | 404 with the same body in both modes. A duress session does not see real Pix and a normal session does not see held Pix, like the statement. |
| 7 | Response time of the new consultation | Non-blocking | It is a routed call, so FT-003 already shapes it. The real consultation makes one simulated core banking hop like the other reads. |
| 8 | Fraud desk | Non-blocking | Unchanged. |
| 9 | Where the status of a real Pix is kept | Non-blocking | Left to the architect. No new SPI is expected: it is state of the demo's fake bank, not of the library. |

## Functional requirements

| Id | Rule | Priority | Requirement |
|---|---|---|---|
| FR-01 | BR-001 | Must | An accepted Pix returns a receipt with status `EM_PROCESSAMENTO` in both modes. |
| FR-02 | BR-002 | Must | The routed interface gets a Pix consultation by end to end id, exposed as `GET /account/pix/{endToEndId}`. It returns the same receipt shape as `POST /account/pix`. |
| FR-03 | BR-003 | Must | In a NORMAL session the consultation returns `EM_PROCESSAMENTO` until the settlement time has passed since the Pix was sent, and `CONCLUIDO` from then on. |
| FR-04 | BR-004 | Must | In a DURESS session the consultation of a held Pix always returns `EM_PROCESSAMENTO`. |
| FR-05 | BR-005 | Must | Apart from the status, a consulted receipt has the same end to end id, key, amount and time as the receipt returned when the Pix was sent. |
| FR-06 | BR-006 | Must | Consulting an id the session does not have fails with 404 and the same body in both modes. |
| FR-07 | BR-007 | Must | The balance is debited when the Pix is accepted, in both modes, whatever the status. |
| FR-08 | BR-008 | Must | The fraud desk still lists the held Pix, and a held Pix never reaches the real account. |
| FR-09 | BR-009 | Should | The settlement time of the real Pix is configurable in the demo (default 5 seconds). |
| FR-10 | BR-010 | Should | The receipt screen shows "Em processamento" and consults the Pix periodically while it is open and the status is not final, with the same code on both phones. |

## Non-functional requirements

| Id | Requirement |
|---|---|
| NFR-01 | No new header, field, cookie or error distinguishes the modes. The receipt and the consultation have the same fields in both sessions (rule 1). |
| NFR-02 | The new method is implemented in the real and the decoy service and has a parity test in the demo (rule 2). |
| NFR-03 | The controller keeps depending only on the routed interface (rule 3). |
| NFR-04 | Settlement uses the injected `Clock`. Tests do not wait for real settlement. |
| NFR-05 | The front end code inside the phone has no mode marker and no per-phone parameter, as in FT-002. |

## Scope

* **Layers affected:** demo (routed interface, real and decoy services, controller, front end, configuration), tests, docs.
* **New files:** a not-found exception shared by real and decoy, test class for this feature, `.agent/plans/pix-em-processamento-tests.md`. Contract and architecture notes if the architect step produces them.
* **Modified files:** `AccountService`, `RealAccountService`, `DecoyAccountService`, `AccountController`, `application.yml`, `static/app.js` (and `index.html` or `styles.css` if the receipt needs it), `DuressFlowTest` and `FrontEndTest` where they expect `CONCLUIDO`, `README.md` (walkthrough and roadmap), `docs/threat-model.md` (receiving side limitation), `.agent/project-reference.md`, `.agent/decisions.md` (new ADR), FT-001 plan and acceptance (AC-03 mentions `CONCLUIDO`).

## Approach

Described at the level of behavior. The class design belongs to the architect.

* The status stays a field of the existing receipt. No DTO gains or loses a field.
* The real service remembers the Pix it sent, so it can answer the consultation and tell when the settlement time has passed.
* The decoy answers the consultation from the held transactions of the current session, so a held Pix needs no new storage.
* The new consultation is one more method of the routed interface. Routing and response time shaping (FT-003) apply to it without changes in the library.
* The receipt screen asks for the Pix again every few seconds while it is open. It stops when the status is `CONCLUIDO` or when the user leaves the screen. Both phones run this same code, so the request pattern differs only by what the server answered.

## Tasks

1. [x] Architect: contract and architecture notes, checked against the threat model and the five rules. Stop for approval.
2. [x] Demo: receipt status `EM_PROCESSAMENTO`, Pix consultation in the routed interface, real and decoy implementations, shared not-found exception, controller endpoint, settlement time property.
3. [x] Front end: status label, receipt polling.
4. [x] Test plan and tests referencing AC-01 to AC-10, including the parity test of the new method. Update the existing tests that expect `CONCLUIDO`.
5. [x] Docs: README walkthrough and roadmap, threat model, project reference, ADR, FT-001 AC-03 wording.
6. [ ] Review against the five rules, `mvn verify`. Commit and PR after Felipe's approval.

## Database changes

None.

## Risks and open questions

1. **A Pix that never settles is itself a pattern.** Someone who knows Decoy Guard could read "always in processing" as a sign of a decoy. An attacker who knows about the duress feature is out of scope in the threat model, and the status buys time, it does not make the deception permanent. To be written in the threat model.
2. **Real customers now see "Em processamento" for a few seconds.** It is how the demo's fake bank behaves from now on, and it is what makes the first receipt identical.
3. **Cross-session consultation.** A duress session that asks for the id of a real Pix sent earlier gets 404. This is consistent with the decoy statement, which does not show the real history either.
4. **README GIF.** `docs/media/demo.gif` shows "Concluído" on the receipt. Not recorded again in this feature. It goes in a later docs PR.
5. **Polling and parity.** Polling stops on `CONCLUIDO`, so the normal phone stops after settlement and the duress phone keeps going while the receipt is open. This follows from the server answer and is visible only to someone inspecting network traffic, which is out of scope.

## Acceptance criteria

* [x] AC-01: A Pix in a normal session returns a receipt with status `EM_PROCESSAMENTO`.
* [x] AC-02: A Pix in a duress session returns a receipt with status `EM_PROCESSAMENTO` and the same fields as the normal one.
* [x] AC-03: In a normal session the consultation returns `EM_PROCESSAMENTO` before the settlement time and `CONCLUIDO` after it.
* [x] AC-04: In a duress session the consultation still returns `EM_PROCESSAMENTO` after the settlement time has passed.
* [x] AC-05: The consulted receipt matches the original one in id, key, amount and time, in both sessions.
* [x] AC-06: Consulting an unknown id returns 404 with the same body in both sessions, and a session cannot consult a Pix of the other mode.
* [x] AC-07: The balance drops as soon as the Pix is accepted in both sessions, while the status is still `EM_PROCESSAMENTO`.
* [x] AC-08: A held Pix in processing appears in the fraud desk view and leaves the real balance unchanged.
* [x] AC-09: The consultation without an open session returns 401.
* [x] AC-10: The front end has a label for `EM_PROCESSAMENTO`, consults the Pix from the phone code and keeps the phone template free of mode markers.

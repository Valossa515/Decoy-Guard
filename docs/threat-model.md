# Threat model

## Scenario

A legitimate account holder, typically elderly, is coerced into using their own bank app. The attacker either watches the screen (in person, by video call, or through screen sharing) or dictates every step over the phone. The victim's credentials, device and biometrics are all genuine, so classic fraud signals (new device, wrong password, unusual location) do not fire.

## Assets

| Asset | Why it matters |
|---|---|
| Account balance and investments | The direct target of the scam |
| Transaction capability (Pix, TED, loans) | Pix is instant and hard to reverse |
| Information about wealth | Tells the attacker whether to escalate or come back later |
| The victim's physical safety | An attacker who feels deceived may become violent |

## Attacker capabilities

**In scope**

* Sees everything rendered on the screen, in real time.
* Dictates navigation, amounts and destination keys.
* Asks the victim to open the balance, statement and receipts to "confirm".
* Has a partner on the receiving end who can check whether a Pix arrived.

**Out of scope for now**

* Malware or rooted device on the victim's phone.
* Network interception or tampering with API traffic.
* Insider access at the bank.
* Attacker who already knows the victim has a duress PIN configured and which one it is.

## Security goals

1. **Indistinguishability.** Nothing observable on screen may reveal the session is a decoy: response shapes, error messages, receipt formats, navigation and, as far as possible, response times.
2. **No real loss.** No value leaves the account and no credit is contracted during a duress session.
3. **Plausibility.** The decoy shows a small balance consistent with a modest account, to lower the attacker's interest, and reacts to the attacker's actions (balance drops after a "successful" transfer).
4. **Out-of-band alert.** The bank, and optionally a trusted contact, is notified through a channel the attacker cannot see.
5. **Fail safe.** If the alert channel fails, the decoy keeps working. A decoy must never be served in a normal session.

## How the current design addresses each goal

| Goal | Mechanism | Status |
|---|---|---|
| Indistinguishability | Real and decoy share the same interface, DTOs, exceptions and receipt id format; the filter never writes the mode to the response | Partial: timing not shaped yet |
| No real loss | Duress calls are routed to the decoy, which only records a `HeldTransaction` | Done in the demo |
| Plausibility | Decoy balance minus held transfers; decoy statement includes them | Done in the demo |
| Out-of-band alert | `DuressActivator` raises one `SilentAlert` per activation through the `SilentAlertPublisher` SPI | SPI done, real channels pending |
| Fail safe | Alert failures are caught; `@Decoy` beans are never autowire candidates, even with the starter disabled | Done |

## Known limitations and open questions

* **The receiving side is the weak spot.** The Pix never arrives, so an attacker with a partner checking the destination account will eventually notice. The decoy buys time for the alert to work; it does not make the deception permanent. Possible mitigation: show the transfer as "em processamento" with a believable delay, mirroring how real Pix sometimes stalls in anti-fraud review.
* **Escalation risk.** If the attacker realizes they were fooled while still with the victim, the victim could be harmed. This is the strongest argument for perfect indistinguishability and for alerting a trusted contact quickly.
* **Response time.** Decoy calls skip core banking and are faster than real ones. Timing is observable, so the decoy should eventually add latency shaped like the real backend.
* **Cognitive load.** A frightened user may not remember a second PIN. Risk signals (active call during a transfer, screen mirroring, remote access apps) should be able to activate duress mode without any action from the user.
* **Thread-bound context.** The mode is bound to the request thread. Work moved to other threads (`@Async`, reactive pipelines) must propagate `DuressContext` explicitly.
* **Data consistency across channels.** Push notifications, SMS, e-mail and internet banking must also respect the decoy, or the attacker can cross-check.
* **Regulation.** Holding a transaction that the user confirmed has to fit the central bank's rules for Pix, including the special return mechanism (MED). This needs review by people who know the regulation.
* **Social engineering of the duress PIN.** Attackers adapt. If the feature becomes widely known, attackers may demand "the other PIN". Mitigations include not advertising which customers have one and allowing biometric triggers.

## Out of scope by design

* Detecting the scam call itself (telecom side).
* Recovering money already sent outside a duress session.
* Replacing the bank's existing anti-fraud scoring. Decoy Guard is a layer that complements it.

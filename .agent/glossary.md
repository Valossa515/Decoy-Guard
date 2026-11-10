# Domain Glossary

## Entities
| Term | Definition | Source class |
|------|-----------|-------------|
| **Duress context** | Snapshot of how a session must be served: session id, mode, trigger, activation time | `DuressContext` |
| **Held transaction** | A transaction shown as successful on screen that never reached the real ledger or payment rail. Kept for the fraud team with the destination the user typed. | `HeldTransaction` |
| **Silent alert** | Notification raised once when a session enters duress, delivered out of band, never on the user's screen | `SilentAlert` |
| **Balance / Statement entry / Pix request / Pix receipt** | DTOs shared by the real and the decoy account | `AccountService.*` (demo) |

## Enum Values / Status Codes
| Enum | Values | Used in |
|------|--------|---------|
| `SessionMode` | NORMAL (real data, real effects), DURESS (decoy data, effects held) | `DuressContext.mode` |
| `ActivationTrigger` | DURESS_CREDENTIAL (duress PIN or biometric), RISK_SIGNAL (active call, screen mirroring, remote access app), MANUAL (operator or trusted contact) | `DuressContext.trigger`, `SilentAlert.trigger` |
| `PixStatus` (demo) | EM_PROCESSAMENTO (accepted, not settled yet. A held Pix stays here), CONCLUIDO (settled, real Pix only) | `PixReceipt.status`, same shape in both modes |

## Business Terms
- **Coercion / duress:** the user is forced, by phone or in person, to use their own bank app.
- **Duress credential:** a second PIN (or fingerprint) registered next to the normal one. Logging in with it opens a decoy session.
- **Decoy session:** a session in DURESS mode. It looks and behaves like a normal session but is served by decoy implementations.
- **Decoy implementation:** a bean annotated `@Decoy` that stands in for one or more routed interfaces.
- **Routed interface:** an interface with a real and a `@Decoy` implementation. Calls on it are routed per session.
- **Indistinguishability:** nothing the attacker can observe (shapes, errors, ids, timing) may differ between modes.
- **Pix:** Brazil's instant payment system. Instant and hard to reverse.
- **End-to-end id:** Pix transaction id, `E` followed by 31 alphanumerics. The decoy uses the same format.
- **MED (Mecanismo Especial de Devolucao):** the central bank's special return mechanism for Pix.
- **Fraud desk:** the bank's back office team that receives alerts and reviews held transactions.
- **Trusted contact:** a person the user chose to be notified during a duress session (planned).
- **Risk signal:** a device-side indicator of coercion (planned Android module).

## Disambiguation
- **Session id vs credential:** the session id is an opaque token, never the user's PIN.
- **Held vs blocked:** a held transaction shows as successful to the user. A blocked one would show an error, which tells the attacker the victim resisted. Decoy Guard holds, it never blocks visibly.
- **Activate vs release:** activate switches a session to DURESS (first activation wins). Release returns it to NORMAL, for example after the bank confirms the user is safe.

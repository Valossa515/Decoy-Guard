# Backlog Rules

## User Story Format
- As a [persona], I want [capability], so that [benefit].
- Personas: account holder under coercion, bank developer integrating the starter, fraud team. See [context.md](context.md#personas).

## Acceptance Criteria
- Given/When/Then, one testable criterion per row in `.agent/specs/acceptance/<feature>.acceptance.toon`.
- Cover the success path and the error paths.
- Any behavior visible to the client must have a parity criterion: same shape, same error, same id format in NORMAL and DURESS.
- Tests reference the criterion ids they cover.

## Business Rules (apply to every story)
- The session mode is never observable by the client.
- No value leaves the account and no credit is contracted during a duress session.
- The decoy reacts plausibly to the attacker's actions (balance drops after a "successful" transfer).
- A failing alert channel never fails the request.
- A decoy is never served in a normal session.

## Definition of Ready
- Spec pair written first: `.agent/specs/features/<feature>.spec.toon` and `.agent/specs/acceptance/<feature>.acceptance.toon`, plus the plan in `.agent/plans/<feature>.md`.
- Specs pass `bash .agent/scripts/validate-spec.sh .agent/specs`.
- Impact on the threat model checked against `docs/threat-model.md`.
- New storage or delivery concerns identified as SPIs.

## Definition of Done
- Code and tests, with tests referencing the acceptance criteria.
- New routed methods implemented in real and decoy, with a parity test in the demo.
- `mvn verify` passes.
- Roadmap checklist in `README.md` updated when an item ships. Threat model updated when a goal's status changes.
- Docs in plain sentences, no em dashes.

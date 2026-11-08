# Decoy Guard

Anti-coercion layer for banking apps: a duress credential opens a decoy session; transfers are held and a silent alert is raised. Read `README.md` for the idea and `docs/threat-model.md` before changing behavior.

## Build

* Java 21, Maven, Spring Boot 4.1.
* `mvn verify` builds and tests everything. Run it before declaring any change done.
* `mvn -pl decoy-guard-demo spring-boot:run` starts the demo on :8080 (PINs 1234 real, 4321 duress).

## Layout

* `decoy-guard-core`: model and SPIs, package `io.github.valossa515.decoyguard.core`. No runtime dependencies, keep it that way.
* `decoy-guard-spring-boot-starter`: auto-configuration, `@Decoy` routing, request filter. Package `io.github.valossa515.decoyguard.spring`.
* `decoy-guard-demo`: fake bank API used for tests and the README walkthrough.
* `docs/specs/`: one spec per feature, as a pair `DG-XXX-name.md` (humans) and `DG-XXX-name.toon` (agents). Keep both in sync.

## Rules that must not break

1. The session mode is never observable by the client: no header, field, cookie, log line returned to the client, or different error.
2. Real and decoy implementations share DTOs, exceptions and id formats. When adding a method to a routed interface, implement it in both and add a parity test in the demo.
3. `@Decoy` beans must never be injectable. Routed services are injected by interface only.
4. Alert failures must never fail the request.
5. Every new storage or delivery concern is an SPI in core with an in-memory or logging default.

## Workflow

* New feature: write the spec pair in `docs/specs/` first, then code, then tests referencing the acceptance criteria.
* Update the roadmap checklist in `README.md` when an item ships.
* Prose in docs and README: plain sentences, no em dashes.

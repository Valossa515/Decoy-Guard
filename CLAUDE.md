# Decoy Guard

Anti-coercion layer for banking apps: a duress credential opens a decoy session; transfers are held and a silent alert is raised. Read `README.md` for the idea and `docs/threat-model.md` before changing behavior.

## Context

This project follows [SDD](https://github.com/valossa515/sdd). The `.agent/` folder is the source of context for agents.

* Read `.agent/SKILLS.md` before every task and follow the skills it links. Its `## Project Overrides` section wins over the base SDD skills (Spring Boot 4.1 instead of 3.x, core without runtime dependencies, mode never observable, routed services injected by interface).
* `.agent/project-reference.md` is the one-page map of the code. `.agent/context.md`, `architecture.md`, `conventions.md`, `glossary.md`, `decisions.md`, `runbook.md` and `backlog_rules.md` hold the details.
* `.agent/skills/`, `.agent/agents/`, `.agent/prompts/` and `.github/agents/` come from the SDD installer. Do not edit them by hand; put project rules in `.agent/SKILLS.md` instead.

## Build

* Java 21, Maven, Spring Boot 4.1.
* `mvn verify` builds and tests everything. Run it before declaring any change done.
* `mvn -pl decoy-guard-demo spring-boot:run` starts the demo on :8080 (PINs 1234 real, 4321 duress).
* `bash .agent/scripts/validate-spec.sh .agent/specs` validates the TOON specs.

## Layout

* `decoy-guard-core`: model and SPIs, package `io.github.valossa515.decoyguard.core`. No runtime dependencies, keep it that way.
* `decoy-guard-spring-boot-starter`: auto-configuration, `@Decoy` routing, request filter. Package `io.github.valossa515.decoyguard.spring`.
* `decoy-guard-demo`: fake bank API used for tests and the README walkthrough.
* `.agent/specs/features/<feature>.spec.toon` and `.agent/specs/acceptance/<feature>.acceptance.toon`: one feature spec and one acceptance file per feature, ids `FT-NNN` (DG-001 is now FT-001).
* `.agent/plans/<feature>.md`: the human readable plan for each feature. Keep it in sync with its specs.

## Rules that must not break

1. The session mode is never observable by the client: no header, field, cookie, log line returned to the client, or different error.
2. Real and decoy implementations share DTOs, exceptions and id formats. When adding a method to a routed interface, implement it in both and add a parity test in the demo.
3. `@Decoy` beans must never be injectable. Routed services are injected by interface only.
4. Alert failures must never fail the request.
5. Every new storage or delivery concern is an SPI in core with an in-memory or logging default.

## Workflow

New features follow the SDD pipeline. The roles live in `.agent/agents/`.

1. **Plan** (`.agent/agents/planner.md`, `@planning`): gap analysis, then the plan in `.agent/plans/`, the feature spec and the acceptance criteria. Validate the specs.
2. **Architect** (`.agent/agents/architect.md`): contract in `.agent/specs/contracts/` and architecture notes in `.agent/plans/<feature>-architecture.md`. Check the design against `docs/threat-model.md` and the rules above.
3. **Implement** (`.agent/agents/builder.md`, `@implementation`): production code only, following the contract or plan.
4. **Test** (`.agent/agents/tester.md`, `@test-plan` and `@test`): test plan in `.agent/plans/<feature>-tests.md`, tests that reference the acceptance criteria ids, parity tests in the demo for routed interfaces.
5. **Review** (`.agent/agents/reviewer.md`, `@review`): structured review, including the five rules above. `mvn verify` must pass. The work then goes up as a PR to `development` and is merged only with CI green.

`.agent/agents/conductor.md` runs the whole pipeline. Small fixes may skip plan and architect, but never test and review.

## Git and pull requests

Details in `CONTRIBUTING.md`.

* All new code goes to `development`, the default branch, through a PR. Nobody pushes to `main` or `development` directly.
* Branches: `feature/ft-NNN-slug`, `fix/slug`, `refactor/slug`, `test/slug`, `docs/slug`, `chore/slug`, `ci/slug`, all from `development`. `hotfix/slug` starts from `main`.
* Commits and PR titles: Conventional Commits in English, `<type>(<scope>): <summary>`, types `feat fix test refactor docs ci build chore`, scopes `core starter demo spec ci docs deps release`. PRs into `development` are squashed, so the PR title is the commit.
* `main` only receives release PRs from `development` (merge commit) and hotfixes.
* A version is a tag `vX.Y.Z` on `main`, created only by the `Release` workflow run by hand. Never create or move version tags yourself.
* CI (`.github/workflows/ci.yml`) runs `build`, `specs`, `pr-title` and `branch-policy`. All must pass. The branch rules are in `.github/rulesets/`.

## Housekeeping

* Update the roadmap checklist in `README.md` when an item ships.
* Record new architecture decisions in `.agent/decisions.md`.
* Prose in docs and README: plain sentences, no em dashes.

---
# .agent/SKILLS.md
# ─────────────────────────────────────────────────────────
# SDD — Skill-Driven Development entry point
# This file is read by the AI agent BEFORE every task.
# Commit this folder alongside your source code.
# ─────────────────────────────────────────────────────────
name: decoy-guard
stack: spring-boot
sdd-version: "1.0.0"
---

# Project Agent Skills

This is a **Spring Boot 4.1 / Java 21** multi-module Maven project (a library plus a demo API).
The base skills target Spring Boot 3.x: where they differ, **Project Overrides** below win.
Before writing any code, read and follow all the skills listed below.

## Active Skills

### Stack Skills

| Skill | File | Scope |
|-------|------|-------|
| Architecture & patterns | [skills/spring-boot/SKILL.md](skills/spring-boot/SKILL.md) | All code, adapted by Project Overrides (Boot 4.1, module layout) |
| Testing | [skills/spring-boot/testing.md](skills/spring-boot/testing.md) | Test files: JUnit 5 + AssertJ, `ApplicationContextRunner`, `MockMvc` |
| REST & API design | [skills/shared/api-design/SKILL.md](skills/shared/api-design/SKILL.md) | Demo controllers and DTOs (DTOs are records shared by real and decoy) |
| Database | [skills/shared/database/SKILL.md](skills/shared/database/SKILL.md) | Storage SPIs only. No database yet, see Project Overrides |
| Security | [skills/shared/security/SKILL.md](skills/shared/security/SKILL.md) | Mode indistinguishability, demo auth, input validation |
| Observability | [skills/shared/observability/SKILL.md](skills/shared/observability/SKILL.md) | Logging that never exposes the session mode to the client |
| Error handling | [skills/shared/error-handling/SKILL.md](skills/shared/error-handling/SKILL.md) | Shared exceptions, identical errors in both modes. No RFC 7807, see Project Overrides |
| Bootstrap | [skills/shared/bootstrap/SKILL.md](skills/shared/bootstrap/SKILL.md) | Project context setup and maintenance |
| Refactor | [skills/shared/refactor/SKILL.md](skills/shared/refactor/SKILL.md) | Convention-aligned refactoring |

### Commands

Workflow commands are handled directly by the agent roles:

| Command | Agent | What it does |
|---------|-------|--------------|
| `@planning` | [agents/planner.md](agents/planner.md) | Plan a feature — produces plan.md + .spec.toon + .acceptance.toon |
| `@implementation` | [agents/builder.md](agents/builder.md) | Execute a plan — implements tasks following conventions |
| `@test-plan` | [agents/tester.md](agents/tester.md) | Design the testing strategy + test plan document |
| `@test` | [agents/tester.md](agents/tester.md) | Implement the tests from the test plan |
| `@review` | [agents/reviewer.md](agents/reviewer.md) | Structured code review with 🔴🟡🔵 severity classification |

### Agent Guardrails

| Skill | File | Scope |
|-------|------|-------|
| Anti-invention | [skills/shared/anti-invention/SKILL.md](skills/shared/anti-invention/SKILL.md) | All tasks — prevents hallucination |
| Pattern analysis | [skills/shared/pattern-analysis/SKILL.md](skills/shared/pattern-analysis/SKILL.md) | All generation — 3-example rule |
| Gap analysis | [skills/shared/gap-analysis/SKILL.md](skills/shared/gap-analysis/SKILL.md) | Planning — detects missing info |
| Spec validation | [skills/shared/spec-validation/SKILL.md](skills/shared/spec-validation/SKILL.md) | Specs — validates structure |
| Definition of Ready | [skills/shared/dor/SKILL.md](skills/shared/dor/SKILL.md) | Pre-implementation gate |
| Definition of Done | [skills/shared/dod/SKILL.md](skills/shared/dod/SKILL.md) | Post-implementation gate |

### Agent Roles

For pipeline-driven development, see [agents/README.md](agents/README.md):

| Role | File | When to use |
|------|------|-------------|
| Planner | [agents/planner.md](agents/planner.md) | Convert requirement → spec |
| Architect | [agents/architect.md](agents/architect.md) | Convert spec → contract |
| Builder | [agents/builder.md](agents/builder.md) | Generate production code |
| Tester | [agents/tester.md](agents/tester.md) | Generate tests |
| Reviewer | [agents/reviewer.md](agents/reviewer.md) | Validate + review |
| Conductor | [agents/conductor.md](agents/conductor.md) | Run full pipeline |

## Project Overrides

Rules here take precedence over the base skills above. They come from `CLAUDE.md` and `docs/threat-model.md`.

### Context documents
- [project-reference.md](project-reference.md)
- [context.md](context.md) (includes the threat model summary and the five invariants)
- [architecture.md](architecture.md)
- [conventions.md](conventions.md)
- [runbook.md](runbook.md)
- [glossary.md](glossary.md)
- [decisions.md](decisions.md)
- [backlog_rules.md](backlog_rules.md)
- [../docs/threat-model.md](../docs/threat-model.md) (read before changing behavior)

### Stack versions (differs from the SDD reference)
- **Spring Boot 4.1** (4.1.2 via `spring-boot-dependencies`) and **Java 21**. The base skills reference Spring Boot 3.x: when an API, starter name or test annotation changed in Boot 4, follow the Boot 4 version already used in this repo. Examples: `spring-boot-starter-webmvc` and `spring-boot-starter-webmvc-test` instead of `spring-boot-starter-web`, `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`, `HttpStatus.UNPROCESSABLE_CONTENT`.
- No Spring Security, no Spring Data JPA, no Hibernate, no PostgreSQL. Do not add them unless a spec asks for it.

### Module boundaries (differs from the SDD layered layout)
- The project is split by Maven module, not by `api/application/domain/infrastructure` packages.
- **`decoy-guard-core` has no runtime dependencies at all.** Not Spring, not SLF4J, not commons-logging, not Jackson. Only test-scoped dependencies are allowed. Log with `System.Logger`.
- Anything that needs Spring or the Servlet API goes in `decoy-guard-spring-boot-starter`.
- `decoy-guard-demo` is a sample app, not part of the library API.

### Package structure
```
io.github.valossa515.decoyguard
├── core/       decoy-guard-core: model records, enums, SPIs, InMemory defaults, DuressActivator
├── spring/     decoy-guard-spring-boot-starter: @Decoy, routing, filter, auto-configuration
└── demo/       decoy-guard-demo
    ├── account/   routed AccountService + DTOs, Real/Decoy implementations, controller
    ├── auth/      login, sessions, interceptor
    └── fraud/     fraud desk (back office stand-in)
```

### Session mode is never observable by the client
- No header, response field, cookie, status code, error message, or log line returned to the client may reveal NORMAL or DURESS.
- Real and decoy implementations share DTOs, exceptions and id formats. A new method on a routed interface is implemented in both, with a parity test in the demo.
- Do not add mode-specific error handlers, response fields or debug endpoints. Timing is observable too: do not make the decoy path noticeably different without a spec.

### Routed services are injected by interface, never by class
- Routing uses interface-based JDK proxies (`proxyTargetClass=false`). Inject `AccountService`, never `RealAccountService` or `DecoyAccountService`.
- `@Decoy` beans must never be injectable. Never inject a decoy directly, never fetch it by name outside `DecoyRoutingPostProcessor`.
- Constructor injection only.

### Storage and delivery
- No database. Every new storage or delivery concern is an SPI in core with an in-memory or logging default, registered with `@ConditionalOnMissingBean`.
- Alert failures must never fail the request.

### Error handling
- No RFC 7807 Problem Details. The demo returns a small JSON map from a controller `@ExceptionHandler` (for example `{"error":"Saldo insuficiente"}` with 422).
- An error must look exactly the same in both modes.

### Authentication
- Out of scope for the library. The demo uses opaque UUID bearer tokens (`Authorization: Bearer <token>`) checked by `SessionInterceptor`. No JWT, no Spring Security.

### Testing
- JUnit 5 + AssertJ. No Mockito: use small fakes and lambdas.
- Core: plain unit tests with `Clock.fixed`.
- Starter: `ApplicationContextRunner` with the auto-configuration and nested test beans.
- Demo (API layer): `@SpringBootTest` + `@AutoConfigureMockMvc`, end to end through HTTP. There is no repository layer and no real database, so the DoD integration-test item is satisfied by these MockMvc tests.
- Tests reference the acceptance criteria they cover. `mvn verify` must pass.

### Specs and workflow
- Specs live in `.agent/specs/features` and `.agent/specs/acceptance`, plans in `.agent/plans`. Validate with `bash .agent/scripts/validate-spec.sh .agent/specs`.
- Feature ids use the SDD format `FT-NNN`. The legacy DG-001 spec is FT-001.
- Write the spec and plan first, then code, then tests.

### Git and pull requests (differs from builder.md, reviewer.md and the DoD)
- Work branches start from `development`, never from `main`: `feature/ft-NNN-slug` for a feature, `fix/`, `refactor/`, `test/`, `docs/`, `chore/` or `ci/` plus a kebab-case slug for the rest. `hotfix/slug` starts from `main`.
- Commit messages follow the full convention in `CONTRIBUTING.md`, not only the `feat`/`fix` of `builder.md`: types `feat fix test refactor docs ci build chore`, scopes `core starter demo spec ci docs deps release`, English, imperative, summary up to 72 characters, `Refs: FT-NNN` footer for feature work.
- The reviewer diffs against `development` (`git diff development..HEAD`), not `main`.
- PRs into `development` are squashed and the PR title must follow the commit convention. That satisfies DoD item 20 (single focused commit).
- Fill in `.github/pull_request_template.md`. CI checks `build`, `specs`, `pr-title` and `branch-policy` must pass before merge.

### Custom rules
- Money is `BigDecimal`. Time comes from an injected `Clock`.
- Docs and README prose: plain sentences, no em dashes.
- Update the roadmap checklist in `README.md` when an item ships.

## How to use

Point your agent at this file before each task:
> "Read `.agent/SKILLS.md` and follow all linked skills before writing any code."

Or configure your IDE plugin to auto-load context from `.agent/`.

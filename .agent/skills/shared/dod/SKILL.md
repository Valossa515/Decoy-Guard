---
name: dod
description: >
  Definition of Done — quality checklist that every deliverable must satisfy
  before it can be considered complete. Ensures consistent quality across
  all implementations.
stack: shared
versions: "SDD 1.x"
---

# Definition of Done (DoD)

A task is **Done** when ALL the following categories pass. This checklist applies to every piece of work — feature, bugfix, or refactor.

---

## Code Quality

| # | Check | Required |
|---|-------|----------|
| 1 | Code compiles / builds without errors | ✅ |
| 2 | No new linter warnings introduced | ✅ |
| 3 | Follows project naming conventions (see stack SKILL.md) | ✅ |
| 4 | Correct architecture layer and package placement | ✅ |
| 5 | No hardcoded values — uses constants or configuration | ✅ |
| 6 | No commented-out code left behind | ✅ |
| 7 | Self-documenting: clear names, small methods, single responsibility | ✅ |

## Testing

| # | Check | Required |
|---|-------|----------|
| 8 | Unit tests cover the service/domain logic | ✅ |
| 9 | Integration tests cover the API/repository layer — or the project's established approach for that layer, as recorded in `.agent/SKILLS.md` → Project Overrides (e.g. rendered-query tests when the suite runs no real database) | ✅ |
| 10 | All tests pass locally | ✅ |
| 11 | Edge cases from the spec are covered | ✅ |
| 12 | Coverage meets project minimum (default: 80%) | ⚠️ |

## Database & Migrations

| # | Check | Required |
|---|-------|----------|
| 13 | Migration script is idempotent and safe to re-run | ✅ |
| 14 | Indexes exist for foreign keys and frequent query columns | ✅ |
| 15 | No breaking schema changes without migration plan | ✅ |

## API Contract

| # | Check | Required |
|---|-------|----------|
| 16 | Endpoint follows REST conventions (see api-design.md) | ✅ |
| 17 | Request/response DTOs match the spec | ✅ |
| 18 | Error responses follow project's error format | ✅ |
| 19 | API documentation is updated (OpenAPI / Swagger) | ⚠️ |

## Delivery

| # | Check | Required |
|---|-------|----------|
| 20 | Changes are in a single, focused commit (or squash-ready) | ✅ |
| 21 | PR description explains what changed and why | ✅ |
| 22 | No unrelated changes are included | ✅ |
| 23 | Technical debt (if any) is documented in a TODO or issue | ⚠️ |

**Legend:** ✅ = must pass, ⚠️ = should pass

---

## Using DoD with the Agent Workflow

When the **reviewer** agent validates a deliverable, it runs through this checklist:

```
For each DoD item:
  IF verifiable by reading code    → CHECK automatically
  IF requires running a command    → ASK the user to confirm
  IF the item fails                → BLOCK and request fixes before approval
```

---

## What NOT to do

- ❌ Marking a task done when tests are missing
- ❌ Skipping migration review because "it's just one column"
- ❌ Merging without confirming the PR description is complete
- ❌ Accepting code that introduces new linter warnings

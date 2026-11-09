---
name: dor
description: >
  Definition of Ready — quality gate checklist that requirements must satisfy
  before implementation begins. Prevents incomplete specs from entering the pipeline.
stack: shared
versions: "SDD 1.x"
---

# Definition of Ready (DoR)

A requirement is **Ready** when it contains enough detail for an agent (or developer) to implement it without guessing. Run this checklist BEFORE starting any implementation.

---

## Requirement Checklist

### Functional Clarity

| # | Check | Required |
|---|-------|----------|
| 1 | The problem being solved is clearly stated | ✅ |
| 2 | Acceptance criteria are defined and testable | ✅ |
| 3 | Business rules are explicit, not implied | ✅ |
| 4 | Input/output data structures are defined | ✅ |
| 5 | Happy path AND error scenarios are described | ✅ |
| 6 | Edge cases are identified (empty lists, nulls, limits) | ⚠️ |

### Technical Readiness

| # | Check | Required |
|---|-------|----------|
| 7 | Target layer/module in the architecture is identified | ✅ |
| 8 | Database changes are specified (tables, columns, indexes) | ✅ |
| 9 | External dependencies are identified (APIs, queues, caches) | ✅ |
| 10 | Breaking changes are flagged (API, DB, config) | ✅ |
| 11 | Performance requirements are stated (if any) | ⚠️ |
| 12 | Security considerations are documented (auth, data sensitivity) | ⚠️ |

### Scope & Estimation

| # | Check | Required |
|---|-------|----------|
| 13 | Scope fits in a single PR (estimated < 500 lines changed) | ✅ |
| 14 | Dependencies on other work are resolved or documented | ✅ |
| 15 | The requirement is independently deliverable | ⚠️ |

**Legend:** ✅ = must have, ⚠️ = should have

---

## How to Use

Before generating any code, run through this checklist. If any ✅ item fails:

```
1. STOP — do not begin implementation
2. LIST — the specific items that failed
3. ASK  — request the missing information from the user
4. WAIT — only proceed once all ✅ items pass
```

For ⚠️ items: proceed with a documented assumption if the information is unavailable, but flag it in the output.

---

## What NOT to do

- ❌ Starting implementation when acceptance criteria are vague
- ❌ Assuming "obvious" business rules without explicit confirmation
- ❌ Implementing without knowing the target architecture layer
- ❌ Ignoring scope — if the task is too large, split it first

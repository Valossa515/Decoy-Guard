---
name: gap-analysis
description: >
  Protocol for detecting missing or ambiguous information in requirements
  before any specification or code is generated. Prevents downstream rework.
stack: shared
versions: "SDD 1.x"
---

# Gap Analysis Protocol

Run this protocol BEFORE generating any specification or code. Its purpose is to detect missing information early, when it's cheap to fix.

## When to Run

- Before the planner writes a feature spec
- Before the architect writes a contract (if planner was skipped)
- Whenever the user provides a new or modified requirement

---

## The Scan

Systematically check the requirement against these categories:

### Business & Domain

| # | Check | Question to Ask if Missing |
|---|-------|---------------------------|
| 1 | Problem statement | "What specific problem are we solving?" |
| 2 | Target user / actor | "Who triggers this action?" |
| 3 | Business rules | "Are there validation rules, limits, or conditions?" |
| 4 | Edge cases | "What happens when input is empty / value is zero / item doesn't exist?" |
| 5 | Error scenarios | "How should the system respond when things go wrong?" |
| 6 | Notifications / side effects | "Should the system notify anyone or trigger other processes?" |

### Data & API

| # | Check | Question to Ask if Missing |
|---|-------|---------------------------|
| 7 | Input fields + types | "What fields does the request have and what are their types?" |
| 8 | Output fields + types | "What should the response contain?" |
| 9 | Required vs optional | "Which fields are mandatory?" |
| 10 | Data relationships | "Does this entity relate to others? How?" |
| 11 | Uniqueness constraints | "Are there fields that must be unique?" |
| 12 | Pagination / filtering | "For list endpoints — is pagination needed?" |

### Technical

| # | Check | Question to Ask if Missing |
|---|-------|---------------------------|
| 13 | Target module / service | "Where in the codebase does this belong?" |
| 14 | External dependencies | "Does this call external APIs or services?" |
| 15 | Database changes | "Does this require new tables, columns, or indexes?" |
| 16 | Auth / permissions | "Who can access this? Are there permission levels?" |
| 17 | Idempotency | "Can this operation be safely retried?" |
| 18 | Async behavior | "Is any part of this asynchronous (events, queues)?" |

---

## Classification

Each gap MUST be classified:

| Classification | Meaning | Action |
|----------------|---------|--------|
| **Blocking** | Different answers produce different specs or code | STOP and ask |
| **Non-blocking** | Has an obvious default; doesn't affect structure | Proceed with documented assumption |

### Decision Criteria

A gap is **blocking** if:
- It affects the data model (new field, new entity, new relationship)
- It changes the API contract (different endpoint, different payload)
- It introduces a business rule (validation, calculation, condition)
- Different developers would make different choices

A gap is **non-blocking** if:
- It's a technical detail with a standard default (e.g., page size = 20)
- The project already has a convention that resolves it
- It doesn't change the structure of the solution

---

## Output Format

Present results to the user:

```
## Gap Analysis Results

| # | Category | Gap | Classification | Resolution |
|---|----------|-----|----------------|------------|
| 1 | Business Rules | Maximum items per order? | 🔴 Blocking | Need answer |
| 2 | Data | Currency format | 🟡 Non-blocking | Assumed BRL in cents |
| 3 | Technical | Pagination for list | 🟡 Non-blocking | Following existing pattern |

**Blocking gaps: 1** — must resolve before proceeding
**Non-blocking assumptions: 2** — documented, will proceed
```

If there are blocking gaps → **STOP** and ask.
If there are zero gaps → state it explicitly: "No gaps found — proceeding."

---

## What NOT to do

- ❌ Skipping gap analysis to "move faster"
- ❌ Silently assuming blocking information
- ❌ Listing dozens of trivial gaps to appear thorough
- ❌ Classifying everything as "non-blocking" to avoid stopping
- ❌ Running gap analysis but ignoring the results

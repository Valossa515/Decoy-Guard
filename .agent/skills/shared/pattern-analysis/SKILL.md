---
name: pattern-analysis
description: >
  Requires the agent to study existing codebase patterns before generating
  new code. Ensures consistency by analyzing at least 3 existing examples.
stack: shared
versions: "SDD 1.x"
---

# Pattern Analysis Protocol

Before writing any new code, the agent MUST study the existing codebase to discover and follow established patterns.

## The 3-Example Rule

> **Analyze at least 3 existing implementations of the same type before generating a new one.**

For example, before creating a new Controller:

1. Find 3 existing controllers in the project
2. Identify the common patterns (imports, annotations, method signatures, error handling)
3. Generate the new controller following those exact patterns

---

## What to Analyze

| Artifact | Look for |
|----------|----------|
| Controllers | Route style, return types, validation approach, exception handling |
| Services | Interface pattern, transaction boundaries, DI style |
| Repositories | Query methods, custom queries, projection usage |
| DTOs | Record vs class, validation annotations, naming |
| Entities | ID type, audit fields, relationship mapping, enum handling |
| Tests | Naming convention, setup pattern, assertion library, mock framework |
| Migrations | Naming scheme, SQL style, index conventions |
| Config files | Property naming, profile organization |

---

## Steps

```
1. SEARCH  — find 3+ existing files of the same artifact type
2. EXTRACT — identify the shared patterns across all examples
3. NOTE    — list the patterns before writing code (in chat, not in files)
4. APPLY   — generate new code following the discovered patterns exactly
5. COMPARE — verify the output looks consistent with the analyzed examples
```

---

## Conflict Resolution

If existing examples are inconsistent (different patterns found):

1. **Most recent wins** — prefer the pattern from the newest file
2. **Majority wins** — prefer the pattern used by 2+ out of 3 examples
3. **Ask** — if no clear winner, ask the user which pattern to follow

---

## What NOT to do

- ❌ Generating code without checking existing patterns first
- ❌ Following a blog post or documentation pattern over the project's own conventions
- ❌ Assuming "best practices" override existing codebase consistency
- ❌ Introducing a new naming convention because it's "cleaner"
- ❌ Changing existing code style in the same PR as a new feature

---
name: anti-invention
description: >
  Guardrails that prevent the AI agent from fabricating requirements, inventing
  APIs, or hallucinating behavior not present in the specification or codebase.
stack: shared
versions: "SDD 1.x"
---

# Anti-Invention Guardrails

These rules apply to **every task**. They prevent the agent from generating code or specifications that go beyond what was explicitly requested.

## Core Principle

> **If it's not in the spec or the codebase, it doesn't exist.**

The agent must work exclusively with:

1. Explicit requirements provided by the user
2. Existing code and patterns found in the repository
3. Active skills and conventions in `.agent/SKILLS.md`

---

## Forbidden Behaviors

| Category | ❌ Forbidden | ✅ Required Instead |
|----------|-------------|---------------------|
| Requirements | Inventing features, endpoints, or fields not requested | Ask the user if the requirement is missing |
| Business rules | Assuming validation rules, limits, or constraints | Ask or document the assumption explicitly |
| Data model | Adding columns, tables, or relationships not in the spec | Only implement what is specified |
| Error handling | Guessing error codes or messages | Follow the project's existing error pattern or ask |
| Dependencies | Adding libraries or frameworks not in the project | Use existing dependencies only |
| Architecture | Creating layers, modules, or services not planned | Follow the existing project structure |

---

## When Information Is Missing

If the agent encounters a gap in the requirements:

```
1. STOP  — do not proceed with assumptions
2. NAME  — identify the specific missing information
3. ASK   — present the question to the user with options if possible
4. WAIT  — do not generate code until the answer is clear
```

**Exception:** If a decision has a single obvious default that aligns with the codebase (e.g., the project already uses UUID for every PK), the agent may proceed but MUST note the assumption inline.

---

## Scope Verification

Before delivering ANY output, the agent verifies:

- [ ] Am I only implementing what was explicitly requested?
- [ ] Am I following existing patterns rather than inventing new ones?
- [ ] Did I avoid adding "nice to have" extras?
- [ ] Is every field, method, and class traceable to a requirement?

If any answer is **no** → remove the extra before delivering.

---

## What NOT to do

- ❌ Adding pagination to an endpoint when only a simple GET was requested
- ❌ Creating audit fields when the spec doesn't mention auditing
- ❌ Inventing DTOs, mappers, or helper classes beyond what the architecture needs
- ❌ Adding logging, metrics, or tracing when not asked for
- ❌ Creating extra test scenarios beyond what the test strategy defines
- ❌ Implementing "future-proofing" abstractions not in the requirements

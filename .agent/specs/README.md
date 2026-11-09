# Specification Schemas

SDD uses **TOON v3.0** (Token-Oriented Object Notation) specifications to keep requirements unambiguous, traceable, and token-efficient. Every line of generated code must trace back to a spec.

## Why TOON

TOON is a compact notation optimized for LLM consumption. Compared to YAML, it reduces token count significantly for tabular data — meaning agents run faster, cheaper, and fit more context in their window.

## TOON Syntax Reference

### Scalars
```toon
key: value
```

### Tabular arrays (compact)
```toon
# Declares N rows with named columns
items[N]{col1,col2,col3}:
  val1a,val1b,val1c
  val2a,val2b,val2c
```

- `[N]` — expected row count (for validation)
- `{col1,col2,...}` — column header declaration
- Each subsequent line is one row, columns separated by `,`
- Trailing empty columns can be omitted (e.g., `AC-001,desc,MUST,,` or `AC-001,desc,MUST`)
- Strings containing commas must use parentheses or be rephrased to avoid commas

### Simple lists
```toon
items[N]:
  value1
  value2
```

### Nested objects
```toon
parent:
  child:
    key: value
```

### Comments
```toon
# This is a comment
```

## Specification Types

| Type | Schema | Purpose | Created by | Extension |
|------|--------|---------|------------|-----------|
| Feature | [feature.schema.md](feature.schema.md) | Business behavior definition | planner | `.spec.toon` |
| Acceptance | [acceptance.schema.md](acceptance.schema.md) | Testable success/failure criteria | planner | `.acceptance.toon` |
| Contract | [contract.schema.md](contract.schema.md) | Technical architecture decisions | architect | `.contract.toon` |

## Flow

```
requirement → feature.spec.toon → acceptance.toon → contract.toon → code
```

Each downstream artifact references its upstream source. This creates a traceability chain from requirement to code.

## Where Specs Live

When installed in a project:

```
.agent/
└── specs/
    ├── features/        ← feature specifications (.spec.toon)
    ├── acceptance/      ← acceptance criteria (.acceptance.toon)
    └── contracts/       ← architecture contracts (.contract.toon)
```

## Validating Specs

`scripts/validate-spec.sh` checks spec files against the schema rules — required blocks and fields, ID formats (`FT-NNN`, `BR-NNN`, `AC-NNN`), unique IDs, declared row counts (`[N]`), allowed enum values, and that `feature-ref` points to an existing feature spec:

```bash
# In the SDD repo (validates the examples):
make validate-specs

# In your project (the installer copies the validator to .agent/scripts/):
bash .agent/scripts/validate-spec.sh .agent/specs
```

Reference examples live in [`specs/examples/`](examples/) and double as test fixtures for the validator in CI.

## Principles

1. **Spec before code** — no code without a spec (or at minimum, a contract)
2. **Traceable** — every spec has an `id` that downstream artifacts reference
3. **Deterministic** — same spec → same code (agents must not add undocumented behavior)
4. **Validated** — specs are checked against schemas before use (see `skills/shared/spec-validation/SKILL.md`)
5. **Token-efficient** — TOON format minimizes token usage for LLM agents

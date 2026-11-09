---
name: spec-validation
description: >
  Rules for validating specification files (feature, acceptance, contract)
  against their schemas. Ensures specs are complete before downstream use.
stack: shared
versions: "SDD 1.x"
---

# Specification Validation

Validate spec files before they are consumed by downstream agents. A spec that fails validation must be fixed before proceeding.

All specs use **TOON v3.0** format. Validation rules apply to the parsed TOON structure.

---

## Validation Levels

### Level 1: Structure

Check that required fields are present:

```
Feature spec (.spec.toon):
  ✓ feature.id exists and matches pattern FT-NNN
  ✓ feature.name is not empty
  ✓ requirement.summary is not empty
  ✓ inputs[] has at least 1 entry
  ✓ outputs.success is defined
  ✓ outputs.errors has at least 1 entry
  ✓ business-rules has at least 1 entry
  ✓ Tabular array counts match declared [N]

Acceptance criteria (.acceptance.toon):
  ✓ acceptance.feature-ref matches an existing feature spec
  ✓ criteria[] has at least 1 entry
  ✓ Each criterion has id, scenario, given, when, then
  ✓ Tabular array column count matches {header} declaration

Architecture contract (.contract.toon):
  ✓ contract.feature-ref matches an existing feature spec
  ✓ At least 1 layer has files defined
  ✓ Each file has path and action
  ✓ test-strategy section is present
  ✓ Tabular array counts match declared [N]
```

### Level 2: Completeness

Check that nothing is missing:

```
Feature → Acceptance:
  ✓ Every error in feature.outputs.errors has ≥1 acceptance criterion
  ✓ Every business rule has ≥1 acceptance criterion

Acceptance → Contract:
  ✓ Every must-have criterion has a test in the test strategy
  ✓ Every acceptance scenario maps to a contract file

Contract → Code:
  ✓ Every file in the contract maps to a feature requirement
  ✓ No orphan files (files not traceable to a requirement)
```

### Level 3: Consistency

Check that specs don't contradict each other:

```
  ✓ Feature input types match contract DTO fields
  ✓ Feature error codes match contract exception types
  ✓ Acceptance scenario count ≥ test strategy scenario count
  ✓ Contract layers match project's architecture pattern
```

---

## Who Validates

| Agent | Validates |
|-------|-----------|
| Planner | Feature spec + acceptance criteria (Level 1 + 2) |
| Architect | Architecture contract (Level 1 + 2 + 3) |
| Reviewer | Complete chain across all specs (Level 1 + 2 + 3) |

---

## Failure Behavior

- **Level 1 failure** → spec is rejected, must fix before proceeding
- **Level 2 failure** → warning, should fix before proceeding
- **Level 3 failure** → investigation needed, may indicate a spec bug

---

## What NOT to do

- ❌ Proceeding with a spec that fails Level 1 validation
- ❌ Ignoring Level 2 warnings without documenting the reason
- ❌ Generating code from an unvalidated spec

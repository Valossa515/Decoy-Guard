---
name: entity
description: >
  Step-by-step guide to generate a domain entity with attributes,
  validations, relationships, and invariant enforcement.
artifact: entity + value objects
---

# Entity Prompt

Generate a domain entity following the project's domain model conventions.

## Required Inputs

| Input | Description |
|-------|-------------|
| `name` | Entity name (e.g., "Order", "Customer") |
| `attributes` | List of fields with types |
| `relationships` | Related entities and cardinality |
| `invariants` | Business rules the entity must enforce |

## Steps

### 1. Analyze existing entities

Find 3+ existing entities in the project. Note:
- ID type and generation strategy (UUID, Long, auto-increment)
- Audit fields (createdAt, updatedAt) — are they present everywhere?
- Relationship mapping style (annotations vs fluent config)
- Enum handling (string, ordinal, separate table)
- Validation approach (constructor, factory method, annotations)

### 2. Create the entity

- Place in the correct domain/model package
- Follow the ID pattern discovered in step 1
- Include audit fields ONLY if the project convention requires them
- Map relationships matching existing patterns

### 3. Implement invariants

For each business rule:
- Add validation in the constructor or factory method
- Throw domain exceptions following the project's exception pattern
- Never silently ignore invalid state

### 4. Create value objects (if needed)

- Small, immutable types for typed attributes (e.g., `Email`, `Money`)
- Place in the same domain package
- Include self-validation

### 5. Verify

- [ ] Same ID strategy as existing entities
- [ ] Audit fields present (only if project-wide pattern exists)
- [ ] All invariants enforced
- [ ] Domain layer has zero framework dependencies
- [ ] Follows naming conventions from SKILL.md

---
name: feature-schema
description: Schema definition for feature specification files (.spec.toon)
---

# Feature Specification Schema

Feature specs define **WHAT** the system should do in business terms. They contain no technical decisions — only behavior.

## Format

All feature specs use **TOON v3.0** (Token-Oriented Object Notation) — a compact, token-efficient format optimized for LLM consumption. See `specs/README.md` for the TOON syntax reference.

## Schema

```toon
# TOON v3.0 – Feature Specification
# .agent/specs/features/{feature-name}.spec.toon

feature:
  id: FT-001
  name: Create Order
  version: 1.0
  status: draft

requirement:
  summary: One paragraph describing the feature from the user's perspective.
  actor: Authenticated User
  trigger: POST /api/v1/orders

inputs[2]{name,type,required,constraints}:
  customer_id,UUID,true,Must reference an existing customer
  items,list,true,At least 1 item and max 50 items

outputs:
  success:
    description: Order is created and returned with generated ID
    status: 201
    body[2]{name,type,value}:
      id,UUID,
      status,string,PENDING
  errors[2]{code,status,when}:
    CUSTOMER_NOT_FOUND,404,customer_id does not exist
    EMPTY_ORDER,422,items list is empty

business-rules[2]{id,rule}:
  BR-001,Order total must be recalculated from item prices × quantities
  BR-002,Stock must be validated before accepting the order

assumptions[2]:
  Currency is always BRL
  Prices are stored in cents (integer)

batches[2]{id,scope,estimate}:
  BATCH-01,Core order creation (happy path),1-2 days
  BATCH-02,Error handling and edge cases,1 day
```

## Required Fields

| Field | Description | Required |
|-------|-------------|----------|
| `feature.id` | Unique identifier (FT-NNN) | ✅ |
| `feature.name` | Human-readable name | ✅ |
| `requirement.summary` | What the feature does | ✅ |
| `inputs` | Input parameters with types and constraints | ✅ |
| `outputs.success` | Expected success response | ✅ |
| `outputs.errors` | Expected error responses | ✅ |
| `business-rules` | Explicit business rules with unique IDs | ✅ |
| `assumptions` | Assumptions made during planning | ⚠️ |
| `batches` | Decomposition for large features | ⚠️ |

## Validation Rules

1. Every input must have `name`, `type`, and `required`
2. Every error must have `code`, `status`, and `when`
3. Business rules must have unique IDs (BR-NNN)
4. If the feature estimate > 2 days, `batches` is required
5. Assumptions must always be flagged explicitly

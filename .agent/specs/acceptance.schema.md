---
name: acceptance-schema
description: Schema definition for acceptance criteria files (.acceptance.toon)
---

# Acceptance Criteria Schema

Acceptance criteria define the testable conditions that prove a feature works correctly. Each criterion maps to at least one test.

## Format

All acceptance specs use **TOON v3.0** (Token-Oriented Object Notation) — a compact, token-efficient format optimized for LLM consumption. See `specs/README.md` for the TOON syntax reference.

## Schema

```toon
# TOON v3.0 – Acceptance Criteria
# .agent/specs/acceptance/{feature-name}.acceptance.toon

acceptance:
  feature-ref: FT-001
  version: 1.0

criteria[4]{id,scenario,given,when,then,priority,business-rule-ref}:
  AC-001,Create order with valid data,A customer with ID exists and items are in stock,POST /api/v1/orders with valid body,Returns 201 with new order ID and status PENDING,must-have,
  AC-002,Reject order for non-existent customer,customer_id does not exist in the database,POST /api/v1/orders with invalid customer_id,Returns 404 with error code CUSTOMER_NOT_FOUND,must-have,
  AC-003,Reject order with empty items,items list is empty,POST /api/v1/orders with empty items,Returns 422 with error code EMPTY_ORDER,must-have,
  AC-004,Verify order total calculation,Order with 2 items: item A (qty 3 price 1000) and item B (qty 1 price 2500),Order is created,Order total equals 5500,must-have,BR-001
```

## Required Fields

| Field | Description | Required |
|-------|-------------|----------|
| `acceptance.feature-ref` | Feature spec this relates to | ✅ |
| `criteria[].id` | Unique ID (AC-NNN) | ✅ |
| `criteria[].scenario` | Short description | ✅ |
| `criteria[].given` | Preconditions | ✅ |
| `criteria[].when` | Action performed | ✅ |
| `criteria[].then` | Expected outcome | ✅ |
| `criteria[].priority` | must-have · should-have · nice-to-have | ✅ |
| `criteria[].business-rule-ref` | Reference to BR-NNN (if applicable) | ⚠️ |

## Validation Rules

1. `feature-ref` must match an existing feature spec ID
2. Every error in the feature spec must have at least one acceptance criterion
3. Every business rule should be covered by at least one criterion
4. Criterion IDs must be unique within a file
5. All `must-have` criteria must be covered by the test strategy in the contract

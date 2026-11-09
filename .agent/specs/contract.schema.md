---
name: contract-schema
description: Schema definition for architecture contract files (.contract.toon)
---

# Architecture Contract Schema

Contracts define the technical implementation plan for a feature. They are **binding**: the builder creates exactly what the contract specifies, nothing more.

## Format

All contract specs use **TOON v3.0** (Token-Oriented Object Notation) — a compact, token-efficient format optimized for LLM consumption. See `specs/README.md` for the TOON syntax reference.

## Schema

```toon
# TOON v3.0 – Architecture Contract
# .agent/specs/contracts/{feature-name}.contract.toon

contract:
  feature-ref: FT-001
  version: 1.0

layers:
  api:
    files[3]{path,action,description}:
      src/main/java/com/example/api/controller/OrderController.java,create,REST endpoint for order creation
      src/main/java/com/example/api/dto/CreateOrderRequest.java,create,Request DTO with validation
      src/main/java/com/example/api/dto/OrderResponse.java,create,Response DTO
  application:
    files[2]{path,action,description}:
      src/main/java/com/example/application/service/OrderService.java,create,Service interface
      src/main/java/com/example/application/service/OrderServiceImpl.java,create,Service implementation with business logic
  domain:
    files[2]{path,action,description}:
      src/main/java/com/example/domain/model/Order.java,create,Order entity with invariants
      src/main/java/com/example/domain/repository/OrderRepository.java,create,Repository port (interface)
  infrastructure:
    files[1]{path,action,description}:
      src/main/java/com/example/infrastructure/persistence/JpaOrderRepository.java,create,JPA repository adapter

migrations[1]{file,description}:
  V3__create_orders_table.sql,Create orders table with indexes

dependencies:
  uses[2]:
    CustomerRepository (existing)
    StockService (existing)
  introduces[0]:

test-strategy:
  unit-tests:
    - target: OrderServiceImpl
      scenarios[4]:
        create-order-happy-path
        customer-not-found
        empty-items-rejected
        total-calculated-correctly
  integration-tests:
    - target: OrderController
      type: controller-slice
      scenarios[3]:
        post-order-returns-201
        post-order-invalid-customer-returns-404
        post-order-empty-items-returns-422
    - target: JpaOrderRepository
      type: database
      scenarios[1]:
        save-and-find-by-id
  excluded[2]{target,reason}:
    CreateOrderRequest,Pure data class with no logic
    OrderResponse,Pure data class with no logic
```

## Required Fields

| Field | Description | Required |
|-------|-------------|----------|
| `contract.feature-ref` | Feature spec reference (FT-NNN) | ✅ |
| `layers.*.files` | Files to create/modify per layer | ✅ |
| `layers.*.files[].action` | `create` or `modify` | ✅ |
| `migrations` | Database changes (empty list if none) | ✅ |
| `dependencies.uses` | Existing code this feature depends on | ✅ |
| `dependencies.introduces` | New dependencies added (empty if none) | ✅ |
| `test-strategy.unit-tests` | Unit tests to create | ✅ |
| `test-strategy.integration-tests` | Integration tests to create | ✅ |
| `test-strategy.excluded` | What NOT to test and why | ✅ |

## Validation Rules

1. Every file must have a valid `action` (`create` or `modify`)
2. Every file in `layers` must map to a requirement in the feature spec
3. `test-strategy` scenarios must cover all `must-have` acceptance criteria
4. `excluded` items must have a `reason`
5. `dependencies.uses` must reference classes that exist in the project
6. No file should appear in multiple layers

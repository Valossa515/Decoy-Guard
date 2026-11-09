---
name: service
description: >
  Step-by-step guide to generate an application service (use case)
  with business logic, transaction handling, and error management.
artifact: service interface + implementation
---

# Service Prompt

Generate an application service following the project's service layer conventions.

## Required Inputs

| Input | Description |
|-------|-------------|
| `name` | Service name (e.g., "OrderService") |
| `operations` | Methods to implement with their behavior |
| `dependencies` | Required repositories and external services |
| `exceptions` | Domain exceptions to throw |

## Steps

### 1. Analyze existing services

Find 3+ existing services in the project. Note:
- Interface + implementation pattern (or direct class)
- Dependency injection style (constructor, field)
- Transaction annotation placement
- Exception handling strategy
- DTO ↔ domain conversion approach

### 2. Create interface (if pattern dictates)

- Define the public contract with domain types in signatures
- Use the project's naming convention (e.g., `{Resource}Service`)

### 3. Create implementation

- Inject dependencies via constructor (never field injection)
- Implement each operation from the spec
- Apply transaction boundaries matching existing services
- Convert between DTOs and domain objects where needed

### 4. Handle errors

- Use existing exception types (don't invent new ones unless needed)
- Follow the project's exception hierarchy
- Propagate domain exceptions — let the controller handle HTTP mapping

### 5. Verify

- [ ] Follows interface/impl pattern (if project uses it)
- [ ] Constructor injection for all dependencies
- [ ] Transaction boundaries match existing services
- [ ] Uses existing repository methods (doesn't invent new ones)
- [ ] Exception handling follows project pattern

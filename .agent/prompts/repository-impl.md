---
name: repository-impl
description: >
  Step-by-step guide to generate a repository implementation
  with query methods and data access configuration.
artifact: repository interface + implementation
---

# Repository Implementation Prompt

Generate a repository following the project's data access conventions.

## Required Inputs

| Input | Description |
|-------|-------------|
| `entity` | The domain entity this repository manages |
| `queries` | Custom query methods needed |
| `id-type` | Primary key type (UUID, Long, etc.) |

## Steps

### 1. Analyze existing repositories

Find 3+ existing repositories in the project. Note:
- Interface location (domain layer vs infrastructure layer)
- Implementation approach (Spring Data JPA, EF Core, custom)
- Custom query patterns (JPQL, native SQL, Criteria, LINQ)
- Projection usage
- Pagination approach

### 2. Create domain interface (port)

- Place in the domain layer
- Define methods using domain types only
- No framework annotations on the interface

### 3. Create infrastructure implementation (adapter)

- Place in the infrastructure layer
- Implement the domain interface
- Use the project's data access framework
- Custom queries follow existing patterns exactly

### 4. Create migration (if contract specifies)

- Follow the project's migration naming scheme
- Use the SQL/config style from existing migrations (see database.md)
- Include indexes for FKs and frequent query columns

### 5. Verify

- [ ] Interface in domain layer, implementation in infrastructure
- [ ] Custom queries follow existing patterns (not a new style)
- [ ] Correct entity type and ID type
- [ ] Follows naming convention from SKILL.md
- [ ] No new framework dependencies introduced

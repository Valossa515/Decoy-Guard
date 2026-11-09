---
name: endpoint
description: >
  Step-by-step guide to generate a REST endpoint including controller,
  request/response DTOs, and wiring to the service layer.
artifact: controller + DTOs
---

# Endpoint Prompt

Generate a REST endpoint following the project's API and controller conventions.

## Required Inputs

| Input | Description |
|-------|-------------|
| `resource` | The resource name (e.g., "orders", "customers") |
| `operation` | HTTP verb + behavior (e.g., "GET by ID", "POST create") |
| `request-body` | Fields for request DTO (if POST/PUT/PATCH) |
| `response-body` | Fields for response DTO |
| `service-method` | The service method this endpoint delegates to |

## Steps

### 1. Analyze existing endpoints

Find 3+ existing controllers in the project. Note:
- Class-level annotations and imports
- Method-level annotations and return types
- DTO naming patterns
- Validation approach (annotations, manual, etc.)
- Error handling pattern

### 2. Create request DTO (if applicable)

- Use the project's DTO pattern (record vs class)
- Add validation annotations matching existing DTOs
- Name following the project's convention (e.g., `Create{Resource}Request`)

### 3. Create response DTO

- Use the project's DTO pattern
- Name following the project's convention (e.g., `{Resource}Response`)
- Include only the fields specified — no extras

### 4. Create or update controller

- Follow the class structure from existing controllers
- Add the new endpoint method
- Wire to the service method via constructor injection
- Return appropriate HTTP status codes:
  - GET → 200
  - POST → 201 + Location header
  - PUT/PATCH → 200
  - DELETE → 204

### 5. Verify

- [ ] Same annotations and style as existing controllers
- [ ] DTO naming follows project convention
- [ ] Validation is consistent with existing endpoints
- [ ] HTTP status code is correct
- [ ] Follows api-design.md conventions (URL structure, pagination, errors)

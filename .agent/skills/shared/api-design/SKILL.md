---
name: api-design
description: REST API conventions applicable to both Spring Boot and .NET projects. Covers URL structure, HTTP verbs, status codes, pagination, versioning, and OpenAPI.
stack: shared
versions: "REST/OpenAPI 3.x"
---

# API Design — Shared Conventions

Apply these conventions regardless of stack (Spring Boot or .NET).

## URL Structure

```
/api/{version}/{resource}
/api/v1/orders
/api/v1/orders/{id}
/api/v1/orders/{orderId}/items
/api/v1/orders/{orderId}/items/{itemId}
```

**Rules:**
- Always plural nouns: `/orders` not `/order`
- Kebab-case for multi-word resources: `/order-items`
- No verbs in URLs: `/orders/{id}/cancel` not `/cancelOrder/{id}`
- Actions as sub-resources or using specific verbs: `POST /orders/{id}/cancellations`

---

## HTTP Verbs & Status Codes

| Operation | Verb | Success Code | Notes |
|-----------|------|-------------|-------|
| List all | GET | 200 | Always return array, never null |
| Get one | GET | 200 / 404 | 404 if not found |
| Create | POST | 201 + Location header | Body = created resource |
| Full update | PUT | 200 | Replace entire resource |
| Partial update | PATCH | 200 | Only update provided fields |
| Delete | DELETE | 204 | No body |
| Long action | POST | 202 Accepted | For async operations |

---

## Pagination

Always paginate list endpoints. Use cursor-based for large datasets, offset for simple cases.

### Offset Pagination (default)

```
GET /api/v1/orders?page=0&size=20&sort=createdAt,desc
```

```json
{
  "data": [...],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

### Cursor Pagination (for feeds, timelines)

```
GET /api/v1/orders?cursor=eyJpZCI6MTAwfQ&limit=20
```

```json
{
  "data": [...],
  "nextCursor": "eyJpZCI6ODB9",
  "hasMore": true
}
```

---

## Error Responses

Use **RFC 7807 Problem Details** for all errors.

```json
{
  "type": "https://api.yourapp.com/errors/validation-failed",
  "title": "Validation failed",
  "status": 422,
  "detail": "One or more fields are invalid",
  "instance": "/api/v1/orders",
  "errors": {
    "customerId": ["CustomerId is required"],
    "items": ["Order must have at least one item"]
  }
}
```

**Status code guide:**
- `400 Bad Request` — malformed syntax, invalid JSON
- `401 Unauthorized` — not authenticated
- `403 Forbidden` — authenticated but not authorized
- `404 Not Found` — resource doesn't exist
- `409 Conflict` — state conflict (e.g., already confirmed)
- `422 Unprocessable Entity` — validation errors (business rules, field errors)
- `429 Too Many Requests` — rate limiting
- `500 Internal Server Error` — unexpected server error

---

## API Versioning

Use URL versioning (simplest, most explicit):

```
/api/v1/orders   ← stable
/api/v2/orders   ← new version with breaking changes
```

- Keep `v1` alive for at least 12 months after `v2` launch
- Document deprecated versions in OpenAPI with `deprecated: true`
- Return `Sunset` header on deprecated endpoints

---

## OpenAPI / Swagger

Always document with OpenAPI 3.0+.

**Required for every endpoint:**
- Summary and description
- All possible response codes
- Request/response schema
- Tag grouping by resource

**Spring Boot:**
```java
@Operation(summary = "Get order by ID")
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Order found",
        content = @Content(schema = @Schema(implementation = OrderResponse.class))),
    @ApiResponse(responseCode = "404", description = "Order not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
})
```

**.NET:**
```csharp
[ProducesResponseType(typeof(OrderResponse), StatusCodes.Status200OK)]
[ProducesResponseType(typeof(ProblemDetails), StatusCodes.Status404NotFound)]
```

---

## Request/Response Conventions

- Always accept and return **JSON** (`Content-Type: application/json`)
- Use **ISO 8601** for dates: `"2024-01-15T10:30:00Z"` (always UTC)
- Use **UUID** for IDs, never sequential integers exposed in URLs
- Never return `null` arrays — return empty `[]` instead
- Avoid nested response wrappers like `{ "data": { ... }, "success": true }` — just return the resource directly for success, RFC 7807 for errors

---

## CORS

Configure explicitly — never use wildcard `*` in production.

```
Access-Control-Allow-Origin: https://yourfrontend.com
Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE
Access-Control-Allow-Headers: Authorization, Content-Type
```

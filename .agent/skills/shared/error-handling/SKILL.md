---
name: error-handling
description: Error handling conventions for Spring Boot and .NET projects. Covers RFC 7807 Problem Details, exception hierarchy, error mapping, and client-friendly responses.
stack: shared
versions: "RFC 7807, Spring Boot 3.x ProblemDetail, ASP.NET Core ProblemDetails"
---

# Error Handling — Shared Conventions

Apply these conventions regardless of stack (Spring Boot or .NET).

## Principles

1. **Use exceptions for exceptional conditions** — not for control flow
2. **Centralize error mapping** — one place converts exceptions to HTTP responses
3. **RFC 7807 Problem Details** — standard error format for all APIs
4. **Never expose internals** — no stack traces, SQL errors, or server paths in responses
5. **Always return actionable messages** — the client should know what to fix

---

## RFC 7807 — Problem Details

All error responses must follow the RFC 7807 format.

### Standard Fields

```json
{
  "type": "https://api.yourapp.com/errors/order-not-found",
  "title": "Order not found",
  "status": 404,
  "detail": "No order found with ID 550e8400-e29b-41d4-a716-446655440000",
  "instance": "/api/v1/orders/550e8400-e29b-41d4-a716-446655440000"
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `type` | Yes | URI reference identifying the error type (link to docs) |
| `title` | Yes | Short, human-readable summary (same for all instances of this type) |
| `status` | Yes | HTTP status code |
| `detail` | Yes | Human-readable explanation specific to this occurrence |
| `instance` | No | URI of the specific request that caused the error |

### Validation Errors (422)

```json
{
  "type": "https://api.yourapp.com/errors/validation-failed",
  "title": "Validation failed",
  "status": 422,
  "detail": "One or more fields are invalid",
  "instance": "/api/v1/orders",
  "errors": {
    "customerId": ["Customer ID is required"],
    "items": ["Order must have at least one item"],
    "items[0].quantity": ["Quantity must be greater than 0"]
  }
}
```

### Business Rule Violations (409/422)

```json
{
  "type": "https://api.yourapp.com/errors/order-already-confirmed",
  "title": "Order already confirmed",
  "status": 409,
  "detail": "Order 550e8400 is already in CONFIRMED status and cannot be modified"
}
```

---

## Exception Hierarchy

Define a clear exception hierarchy. Keep it simple — avoid dozens of exception classes.

### Recommended Base Exceptions

```
ApplicationException (abstract base)
├── NotFoundException         → 404
├── ValidationException       → 422
├── ConflictException         → 409
├── ForbiddenException        → 403
└── ExternalServiceException  → 502
```

### Spring Boot

```java
// Base exception
public abstract class ApplicationException extends RuntimeException {
    private final String errorType;

    protected ApplicationException(String errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public String getErrorType() { return errorType; }
}

// Concrete exceptions
public class NotFoundException extends ApplicationException {
    public NotFoundException(String resource, Object id) {
        super("not-found", "%s not found with ID %s".formatted(resource, id));
    }
}

public class OrderNotFoundException extends NotFoundException {
    public OrderNotFoundException(UUID id) {
        super("Order", id);
    }
}

public class ConflictException extends ApplicationException {
    public ConflictException(String message) {
        super("conflict", message);
    }
}
```

### .NET

```csharp
// Base exception
public abstract class ApplicationException : Exception
{
    public string ErrorType { get; }

    protected ApplicationException(string errorType, string message)
        : base(message)
    {
        ErrorType = errorType;
    }
}

// Concrete exceptions
public class NotFoundException : ApplicationException
{
    public NotFoundException(string resource, object id)
        : base("not-found", $"{resource} not found with ID {id}") { }
}

public class OrderNotFoundException : NotFoundException
{
    public OrderNotFoundException(Guid id) : base("Order", id) { }
}

public class ConflictException : ApplicationException
{
    public ConflictException(string message) : base("conflict", message) { }
}
```

---

## Centralized Error Mapping

### Spring Boot — @RestControllerAdvice

```java
@RestControllerAdvice
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final String BASE_URI = "https://api.yourapp.com/errors/";

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(
            NotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource not found");
        problem.setType(URI.create(BASE_URI + ex.getErrorType()));
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(
            ConflictException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("State conflict");
        problem.setType(URI.create(BASE_URI + ex.getErrorType()));
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle("Validation failed");
        problem.setDetail("One or more fields are invalid");
        problem.setType(URI.create(BASE_URI + "validation-failed"));
        problem.setInstance(URI.create(request.getRequestURI()));

        Map<String, List<String>> errors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.groupingBy(
                FieldError::getField,
                Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())));
        problem.setProperty("errors", errors);

        return ResponseEntity.unprocessableEntity().body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal server error");
        problem.setType(URI.create(BASE_URI + "internal-error"));
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.internalServerError().body(problem);
    }
}
```

### .NET — Exception Handling Middleware

```csharp
public class GlobalExceptionMiddleware(
    RequestDelegate next,
    ILogger<GlobalExceptionMiddleware> logger)
{
    private const string BaseUri = "https://api.yourapp.com/errors/";

    public async Task InvokeAsync(HttpContext context)
    {
        try
        {
            await next(context);
        }
        catch (Exception ex)
        {
            await HandleExceptionAsync(context, ex);
        }
    }

    private async Task HandleExceptionAsync(HttpContext context, Exception exception)
    {
        var (statusCode, title, errorType) = exception switch
        {
            NotFoundException ex => (404, "Resource not found", ex.ErrorType),
            ConflictException ex => (409, "State conflict", ex.ErrorType),
            FluentValidation.ValidationException => (422, "Validation failed", "validation-failed"),
            _ => (500, "Internal server error", "internal-error")
        };

        if (statusCode == 500)
            logger.LogError(exception, "Unexpected error at {Path}", context.Request.Path);

        var problemDetails = new ProblemDetails
        {
            Type = BaseUri + errorType,
            Title = title,
            Status = statusCode,
            Detail = statusCode == 500 ? "An unexpected error occurred" : exception.Message,
            Instance = context.Request.Path
        };

        if (exception is FluentValidation.ValidationException validationEx)
        {
            problemDetails.Extensions["errors"] = validationEx.Errors
                .GroupBy(e => e.PropertyName)
                .ToDictionary(g => g.Key, g => g.Select(e => e.ErrorMessage).ToArray());
        }

        context.Response.StatusCode = statusCode;
        context.Response.ContentType = "application/problem+json";
        await context.Response.WriteAsJsonAsync(problemDetails);
    }
}
```

---

## Status Code Decision Tree

```
Is the request malformed (bad JSON, missing Content-Type)?
  → 400 Bad Request

Is the user not authenticated?
  → 401 Unauthorized

Is the user authenticated but lacks permissions?
  → 403 Forbidden

Does the requested resource not exist?
  → 404 Not Found

Is there a state conflict (duplicate, already processed)?
  → 409 Conflict

Are there validation errors (business rules, field errors)?
  → 422 Unprocessable Entity

Is the client sending too many requests?
  → 429 Too Many Requests

Is an external dependency failing?
  → 502 Bad Gateway

Is the server overloaded?
  → 503 Service Unavailable

Is it an unexpected server error?
  → 500 Internal Server Error
```

---

## Error Logging Rules

| Status Range | Log Level | What to log |
|-------------|-----------|-------------|
| 4xx (client) | `WARN` | Request path, error type, user ID (if available) |
| 404 | `DEBUG` | Only log if suspicious (many 404s may indicate scanning) |
| 422 | `INFO` | Validation errors help identify UX issues |
| 5xx (server) | `ERROR` | Full exception with stack trace, request context |

---

## What NOT to do

- Return plain text error messages — always use RFC 7807 Problem Details
- Expose stack traces in production responses
- Catch `Exception` silently — always log or rethrow
- Use HTTP 200 with `{ "success": false }` — use proper status codes
- Create one exception class per use case — keep the hierarchy shallow
- Return different error formats from different endpoints
- Use `throws Exception` / `throw new Exception()` — use specific types
- Log 4xx errors at ERROR level — they are client errors, not server failures
- Retry on 4xx — retrying won't fix client errors (only retry on 5xx/network)

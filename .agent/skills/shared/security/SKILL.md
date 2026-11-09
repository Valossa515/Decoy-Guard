---
name: security
description: Security conventions for Spring Boot and .NET projects. Covers authentication (JWT, OAuth2), authorization, CORS, CSRF, input validation, secrets management, and OWASP Top 10 prevention.
stack: shared
versions: "Spring Security 6.x, ASP.NET Core Identity, OAuth2/OIDC"
---

# Security — Shared Conventions

Apply these conventions regardless of stack (Spring Boot or .NET).

## Authentication — JWT

Use stateless JWT-based authentication for APIs.

### Token Structure

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "roles": ["ROLE_USER", "ROLE_ADMIN"],
  "iat": 1700000000,
  "exp": 1700003600,
  "iss": "https://yourapp.com"
}
```

**Rules:**
- Access tokens: short-lived (15–30 minutes)
- Refresh tokens: long-lived (7–30 days), stored securely (HttpOnly cookie or encrypted DB)
- Always validate `iss`, `aud`, `exp` claims
- Never store sensitive data in JWT payload (it's base64, not encrypted)
- Use asymmetric keys (RS256/ES256) for microservices; HMAC (HS256) only for monoliths

---

### Spring Boot — Spring Security 6

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable) // Stateless API — no CSRF needed
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${app.security.jwt.secret}") String secret) {
        return NimbusJwtDecoder.withSecretKeyValue(
            new SecretKeySpec(secret.getBytes(), "HmacSHA256")).build();
    }
}
```

**Method-level security:**
```java
@PreAuthorize("hasRole('ADMIN')")
public void deleteUser(UUID id) { ... }

@PreAuthorize("#userId == authentication.principal.claims['sub']")
public OrderResponse getOrder(UUID userId, UUID orderId) { ... }
```

---

### .NET — ASP.NET Core JWT

```csharp
// Program.cs
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidateAudience = true,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            ValidIssuer = builder.Configuration["Jwt:Issuer"],
            ValidAudience = builder.Configuration["Jwt:Audience"],
            IssuerSigningKey = new SymmetricSecurityKey(
                Encoding.UTF8.GetBytes(builder.Configuration["Jwt:Key"]!)),
            ClockSkew = TimeSpan.Zero // Don't tolerate expired tokens
        };
    });

builder.Services.AddAuthorization(options =>
{
    options.AddPolicy("AdminOnly", policy => policy.RequireRole("Admin"));
    options.AddPolicy("CanManageOrders", policy =>
        policy.RequireClaim("permission", "orders:write"));
});
```

**Controller-level authorization:**
```csharp
[Authorize]
[ApiController]
[Route("api/v1/[controller]")]
public class OrdersController : ControllerBase
{
    [Authorize(Policy = "AdminOnly")]
    [HttpDelete("{id:guid}")]
    public async Task<IActionResult> Delete(Guid id, CancellationToken ct) { ... }
}
```

---

## OAuth2 / OpenID Connect

For user-facing apps, prefer OAuth2 + OIDC with an identity provider (Keycloak, Auth0, Entra ID).

**Rules:**
- Use Authorization Code + PKCE flow for SPAs and mobile apps
- Never use Implicit flow (deprecated)
- Validate `id_token` signature and claims on the backend
- Store tokens server-side when possible; use BFF (Backend For Frontend) pattern

---

## CORS

Configure explicitly per environment — **never** use wildcard `*` in production.

```
Access-Control-Allow-Origin: https://yourfrontend.com
Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS
Access-Control-Allow-Headers: Authorization, Content-Type
Access-Control-Max-Age: 3600
Access-Control-Allow-Credentials: true
```

**Spring Boot:**
```java
@Bean
public CorsConfigurationSource corsConfigurationSource(
        @Value("${app.cors.allowed-origins}") List<String> origins) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(origins);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
}
```

**.NET:**
```csharp
builder.Services.AddCors(options =>
{
    options.AddPolicy("Default", policy =>
    {
        policy.WithOrigins(builder.Configuration.GetSection("Cors:AllowedOrigins").Get<string[]>()!)
              .AllowAnyMethod()
              .AllowAnyHeader()
              .AllowCredentials();
    });
});

// In middleware pipeline
app.UseCors("Default");
```

---

## Input Validation

Validate all external input at system boundaries. Never trust client data.

**Rules:**
- Validate request DTOs with Bean Validation (Spring) or FluentValidation (.NET)
- Sanitize strings that will be rendered in HTML (prevent XSS)
- Use parameterized queries — never concatenate user input into SQL
- Validate UUIDs, emails, and enums before processing
- Reject unexpected fields (strict deserialization)
- Set max request body size limits

**Spring Boot:**
```java
public record CreateUserRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8, max = 128) String password
) {}
```

**.NET:**
```csharp
public class CreateUserCommandValidator : AbstractValidator<CreateUserCommand>
{
    public CreateUserCommandValidator()
    {
        RuleFor(x => x.Name).NotEmpty().MaximumLength(100);
        RuleFor(x => x.Email).NotEmpty().EmailAddress();
        RuleFor(x => x.Password).NotEmpty().MinimumLength(8).MaximumLength(128);
    }
}
```

---

## Secrets Management

**Rules:**
- Never commit secrets to version control (API keys, DB passwords, JWT secrets)
- Use environment variables or a secrets manager (AWS Secrets Manager, Azure Key Vault, HashiCorp Vault)
- Add `.env` to `.gitignore`
- Rotate secrets periodically
- Use different secrets per environment (dev, staging, prod)

**Spring Boot — application.yml:**
```yaml
app:
  security:
    jwt:
      secret: ${JWT_SECRET}
      expiration: 30m
  payment:
    api-key: ${PAYMENT_API_KEY}
```

**.NET — appsettings.json + User Secrets:**
```json
{
  "Jwt": {
    "Key": "FROM_ENVIRONMENT_VARIABLE",
    "Issuer": "https://yourapp.com",
    "Audience": "https://yourapp.com"
  }
}
```

```bash
# Development only
dotnet user-secrets set "Jwt:Key" "your-dev-secret-key"
```

---

## OWASP Top 10 Prevention Checklist

| Threat | Prevention |
|--------|-----------|
| **Injection** (SQL, NoSQL, LDAP) | Parameterized queries, ORM (JPA/EF Core), never concatenate input |
| **Broken Authentication** | Strong passwords, rate limiting on login, account lockout, MFA |
| **Sensitive Data Exposure** | HTTPS everywhere, encrypt at rest, don't log PII/secrets |
| **XXE** | Disable external entity processing in XML parsers |
| **Broken Access Control** | Authorize at method level, validate resource ownership |
| **Security Misconfiguration** | No default credentials, disable debug in production, minimal permissions |
| **XSS** | Encode output, CSP headers, avoid `innerHTML` / `@Html.Raw()` |
| **Insecure Deserialization** | Validate types, use strict DTOs, reject unknown properties |
| **Vulnerable Dependencies** | Dependabot / `dotnet list package --vulnerable`, regular updates |
| **Insufficient Logging** | Log auth events, access denials, input validation failures |

---

## Security Headers

Return these headers on every response:

```
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Content-Security-Policy: default-src 'self'
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: camera=(), microphone=(), geolocation=()
```

**Spring Boot:**
```java
http.headers(headers -> headers
    .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
    .contentTypeOptions(Customizer.withDefaults())
    .httpStrictTransportSecurity(hsts -> hsts
        .includeSubDomains(true)
        .maxAgeInSeconds(31536000))
);
```

**.NET:**
```csharp
app.UseHsts();
app.Use(async (context, next) =>
{
    context.Response.Headers.Append("X-Content-Type-Options", "nosniff");
    context.Response.Headers.Append("X-Frame-Options", "DENY");
    context.Response.Headers.Append("Referrer-Policy", "strict-origin-when-cross-origin");
    await next();
});
```

---

## Rate Limiting

Protect endpoints from abuse.

**Spring Boot (using Bucket4j or Spring Cloud Gateway):**
```yaml
app:
  rate-limit:
    login:
      capacity: 5
      refill-tokens: 5
      refill-duration: 1m
    api:
      capacity: 100
      refill-tokens: 100
      refill-duration: 1m
```

**.NET (built-in rate limiting, .NET 7+):**
```csharp
builder.Services.AddRateLimiter(options =>
{
    options.AddFixedWindowLimiter("api", opt =>
    {
        opt.PermitLimit = 100;
        opt.Window = TimeSpan.FromMinutes(1);
        opt.QueueLimit = 0;
    });

    options.OnRejected = async (context, ct) =>
    {
        context.HttpContext.Response.StatusCode = StatusCodes.Status429TooManyRequests;
        await context.HttpContext.Response.WriteAsJsonAsync(
            new ProblemDetails { Status = 429, Title = "Too many requests" }, ct);
    };
});
```

---

## What NOT to do

- Store passwords in plain text — always hash with bcrypt/Argon2
- Use `MD5` or `SHA1` for password hashing
- Disable HTTPS in production
- Return stack traces in production error responses
- Use wildcard `*` for CORS origins in production
- Store JWT secrets in source code
- Trust `X-Forwarded-For` without proxy validation
- Use `@Secured` or `[AllowAnonymous]` without understanding the implications
- Skip input validation because "the frontend validates"
- Log sensitive data (passwords, tokens, credit card numbers)

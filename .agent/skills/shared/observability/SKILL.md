---
name: observability
description: Observability conventions for Spring Boot and .NET projects. Covers structured logging, distributed tracing, health checks, metrics, and alerting patterns.
stack: shared
versions: "Micrometer/Prometheus, OpenTelemetry, Serilog/SLF4J"
---

# Observability — Shared Conventions

Apply these conventions regardless of stack (Spring Boot or .NET).

## Structured Logging

Always use structured logging with contextual fields. Never use string concatenation for log messages.

### Log Levels

| Level | When to use |
|-------|-----------|
| **ERROR** | Unexpected failures requiring investigation (DB down, external service error) |
| **WARN** | Recoverable issues (retry succeeded, deprecated API called, rate limit hit) |
| **INFO** | Business-relevant events (order created, payment processed, user registered) |
| **DEBUG** | Technical details useful during development (SQL queries, cache hits/misses) |
| **TRACE** | Very verbose, method-level tracing (rarely used in production) |

**Rules:**
- Always include correlation/trace IDs in log entries
- Log at method boundaries for important operations (start + result)
- Never log sensitive data (passwords, tokens, PII, credit card numbers)
- Use structured fields, not string interpolation
- Include `userId`, `orderId`, `traceId` as contextual fields

---

### Spring Boot — SLF4J + Logback

```java
@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Override
    public OrderResponse create(CreateOrderRequest request) {
        log.info("Creating order for customer={}", request.customerId());

        Order order = orderRepository.save(Order.create(request));

        log.info("Order created orderId={} customerId={} total={}",
            order.getId(), order.getCustomerId(), order.getTotal());

        return OrderResponse.from(order);
    }
}
```

**Structured JSON logging (application.yml):**
```yaml
logging:
  level:
    root: INFO
    com.example: DEBUG
    org.springframework: WARN
  pattern:
    console: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n"

# For production — use JSON format with logstash-logback-encoder
# Add to pom.xml: net.logstash.logback:logstash-logback-encoder
```

**logback-spring.xml (production):**
```xml
<configuration>
  <springProfile name="prod">
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>traceId</includeMdcKeyName>
        <includeMdcKeyName>spanId</includeMdcKeyName>
        <includeMdcKeyName>userId</includeMdcKeyName>
      </encoder>
    </appender>
    <root level="INFO">
      <appender-ref ref="JSON" />
    </root>
  </springProfile>
</configuration>
```

---

### .NET — Serilog

```csharp
// Program.cs
builder.Host.UseSerilog((context, config) =>
{
    config
        .ReadFrom.Configuration(context.Configuration)
        .Enrich.FromLogContext()
        .Enrich.WithMachineName()
        .Enrich.WithProperty("Application", "MyApp")
        .WriteTo.Console(new RenderedCompactJsonFormatter());
});
```

```csharp
public class CreateOrderCommandHandler(
    IOrderRepository orderRepository,
    ILogger<CreateOrderCommandHandler> logger
) : IRequestHandler<CreateOrderCommand, OrderResponse>
{
    public async Task<OrderResponse> Handle(
        CreateOrderCommand request, CancellationToken ct)
    {
        logger.LogInformation("Creating order for CustomerId={CustomerId}", request.CustomerId);

        var order = Order.Create(request);
        await orderRepository.AddAsync(order, ct);

        logger.LogInformation("Order created OrderId={OrderId} CustomerId={CustomerId}",
            order.Id, request.CustomerId);

        return OrderResponse.FromDomain(order);
    }
}
```

**appsettings.json:**
```json
{
  "Serilog": {
    "MinimumLevel": {
      "Default": "Information",
      "Override": {
        "Microsoft.AspNetCore": "Warning",
        "Microsoft.EntityFrameworkCore": "Warning"
      }
    }
  }
}
```

---

## Distributed Tracing

Use OpenTelemetry for tracing across services.

**Rules:**
- Every incoming HTTP request must have a trace ID
- Propagate trace IDs to downstream HTTP calls and message queues
- Add custom spans for significant business operations
- Include trace ID in error responses for debugging

### Spring Boot — Micrometer Tracing + OpenTelemetry

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

```yaml
# application.yml
management:
  tracing:
    sampling:
      probability: 1.0  # 100% in dev, lower in prod
  otlp:
    tracing:
      endpoint: http://otel-collector:4318/v1/traces
```

### .NET — OpenTelemetry

```csharp
builder.Services.AddOpenTelemetry()
    .WithTracing(tracing => tracing
        .AddAspNetCoreInstrumentation()
        .AddHttpClientInstrumentation()
        .AddEntityFrameworkCoreInstrumentation()
        .AddOtlpExporter(opts =>
            opts.Endpoint = new Uri(builder.Configuration["Otlp:Endpoint"]!)));
```

---

## Health Checks

Expose health endpoints for load balancers and orchestrators.

### Endpoint Convention

| Endpoint | Purpose |
|----------|---------|
| `GET /actuator/health` (Spring) / `GET /health` (.NET) | Overall status |
| `GET /actuator/health/liveness` / `GET /health/live` | Is the process alive? |
| `GET /actuator/health/readiness` / `GET /health/ready` | Can it accept traffic? |

**Rules:**
- Liveness: only returns UP if the process is running (no dependency checks)
- Readiness: checks database connectivity, external services, message broker
- Return `200 OK` when healthy, `503 Service Unavailable` when unhealthy
- Never expose detailed health info in production (e.g., DB connection strings)

### Spring Boot — Actuator

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true
  health:
    db:
      enabled: true
    diskspace:
      enabled: true
```

### .NET — Health Checks

```csharp
builder.Services.AddHealthChecks()
    .AddNpgSql(builder.Configuration.GetConnectionString("Default")!)
    .AddRedis(builder.Configuration.GetConnectionString("Redis")!)
    .AddUrlGroup(new Uri("https://external-api.com/health"), "external-api");

app.MapHealthChecks("/health/live", new HealthCheckOptions
{
    Predicate = _ => false // No dependency checks for liveness
});

app.MapHealthChecks("/health/ready", new HealthCheckOptions
{
    ResponseWriter = UIResponseWriter.WriteHealthCheckUIResponse
});
```

---

## Metrics

Expose application metrics for monitoring and alerting.

### Key Metrics to Track

| Category | Metric | Example |
|----------|--------|---------|
| **HTTP** | Request rate, latency, error rate | `http_server_requests_seconds` |
| **Business** | Orders created, payments processed | `orders_created_total` |
| **Database** | Connection pool size, query duration | `db_pool_active_connections` |
| **JVM/.NET** | Memory, GC, threads | `jvm_memory_used_bytes` |
| **External** | Outbound call latency, error rate | `http_client_requests_seconds` |

### Spring Boot — Micrometer + Prometheus

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus
  metrics:
    tags:
      application: ${spring.application.name}
```

```java
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final MeterRegistry meterRegistry;

    @Override
    public OrderResponse create(CreateOrderRequest request) {
        Order order = orderRepository.save(Order.create(request));
        meterRegistry.counter("orders.created",
            "status", order.getStatus().name()).increment();
        return OrderResponse.from(order);
    }
}
```

### .NET — Prometheus / OpenTelemetry Metrics

```csharp
builder.Services.AddOpenTelemetry()
    .WithMetrics(metrics => metrics
        .AddAspNetCoreInstrumentation()
        .AddRuntimeInstrumentation()
        .AddPrometheusExporter());

app.MapPrometheusScrapingEndpoint();
```

```csharp
public class OrderMetrics
{
    private readonly Counter<int> _ordersCreated;

    public OrderMetrics(IMeterFactory meterFactory)
    {
        var meter = meterFactory.Create("MyApp.Orders");
        _ordersCreated = meter.CreateCounter<int>("orders.created");
    }

    public void OrderCreated(string status) =>
        _ordersCreated.Add(1, new KeyValuePair<string, object?>("status", status));
}
```

---

## Alerting Rules

Define alerts based on SLOs (Service Level Objectives).

| Alert | Condition | Severity |
|-------|-----------|----------|
| High error rate | HTTP 5xx > 1% for 5 min | Critical |
| High latency | p99 > 2s for 5 min | Warning |
| Database down | Health check failing for 1 min | Critical |
| Disk space low | Available < 10% | Warning |
| Memory pressure | JVM/CLR heap > 85% for 10 min | Warning |

---

## What NOT to do

- Use `System.out.println` / `Console.WriteLine` instead of a logging framework
- Log full request/response bodies in production (performance + PII risk)
- Use `log.error()` for expected business exceptions (use `warn` or `info`)
- Skip trace ID propagation to downstream services
- Expose `/actuator` or detailed health info without authentication in production
- Create metrics with unbounded cardinality (e.g., userId as a metric tag)
- Ignore alerts — every alert should be actionable or removed

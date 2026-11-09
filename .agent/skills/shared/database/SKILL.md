---
name: database
description: Database conventions for Spring Boot (JPA/Hibernate) and .NET (EF Core) projects. Covers schema design, migrations, query patterns, and performance.
stack: shared
versions: "PostgreSQL 16+, Flyway or EF Migrations"
---

# Database — Shared Conventions

## Schema Design

**Naming:**
- Tables: `snake_case` plural — `orders`, `order_items`, `customer_addresses`
- Columns: `snake_case` — `created_at`, `customer_id`
- PKs: always `id` (UUID)
- FKs: `{referenced_table_singular}_id` — `customer_id`, `order_id`
- Indexes: `idx_{table}_{column(s)}` — `idx_orders_customer_id`
- Unique constraints: `uq_{table}_{column(s)}` — `uq_users_email`

**Always include on every table:**
```sql
id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
```

---

## Migrations

Use version-controlled migrations. **Never** use `ddl-auto: create` or `auto-migrate` in production.

### Spring Boot — Flyway

```
src/main/resources/db/migration/
├── V1__create_orders_table.sql
├── V2__create_order_items_table.sql
└── V3__add_index_orders_customer_id.sql
```

```sql
-- V1__create_orders_table.sql
CREATE TABLE orders (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID         NOT NULL,
    status      VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_customer_id ON orders(customer_id);
```

```yaml
# application.yml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
```

### .NET — EF Core Migrations

```bash
# Create migration
dotnet ef migrations add CreateOrdersTable --project Infrastructure --startup-project Api

# Apply
dotnet ef database update --project Infrastructure --startup-project Api
```

```csharp
// Always configure explicitly in OnModelCreating, not via annotations
protected override void OnModelCreating(ModelBuilder modelBuilder)
{
    modelBuilder.ApplyConfigurationsFromAssembly(typeof(InfrastructureAssembly).Assembly);
}

// OrderConfiguration.cs
public class OrderConfiguration : IEntityTypeConfiguration<Order>
{
    public void Configure(EntityTypeBuilder<Order> builder)
    {
        builder.ToTable("orders");
        builder.HasKey(o => o.Id);
        builder.Property(o => o.Status).HasConversion<string>().HasMaxLength(50);
        builder.HasIndex(o => o.CustomerId).HasDatabaseName("idx_orders_customer_id");
    }
}
```

---

## Query Patterns

### N+1 Prevention

**Spring Boot — always use fetch join for collections:**
```java
@Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
Optional<Order> findByIdWithItems(@Param("id") UUID id);
```

**.NET — always use Include for related data:**
```csharp
var order = await _context.Orders
    .Include(o => o.Items)
    .FirstOrDefaultAsync(o => o.Id == id, ct);
```

### Projections for Read Queries

Avoid loading full entities when you only need a few fields.

**Spring Boot — interface projection:**
```java
public interface OrderSummary {
    UUID getId();
    String getStatus();
    Instant getCreatedAt();
}

List<OrderSummary> findAllByCustomerId(String customerId);
```

**.NET — select into DTO:**
```csharp
var summaries = await _context.Orders
    .Where(o => o.CustomerId == customerId)
    .Select(o => new OrderSummaryResponse(o.Id, o.Status, o.CreatedAt))
    .ToListAsync(ct);
```

### Pagination Queries

**Spring Boot:**
```java
Page<Order> findByCustomerId(String customerId, Pageable pageable);
// Usage: PageRequest.of(0, 20, Sort.by("createdAt").descending())
```

**.NET:**
```csharp
var orders = await _context.Orders
    .Where(o => o.CustomerId == customerId)
    .OrderByDescending(o => o.CreatedAt)
    .Skip(page * size)
    .Take(size)
    .ToListAsync(ct);

var total = await _context.Orders.CountAsync(o => o.CustomerId == customerId, ct);
```

---

## Soft Delete

Use `deleted_at` column instead of physically deleting rows.

```sql
ALTER TABLE orders ADD COLUMN deleted_at TIMESTAMPTZ NULL;
CREATE INDEX idx_orders_deleted_at ON orders(deleted_at) WHERE deleted_at IS NULL;
```

**Spring Boot:**
```java
@Entity
@SQLRestriction("deleted_at IS NULL") // Hibernate 6+
@SQLDelete(sql = "UPDATE orders SET deleted_at = NOW() WHERE id = ?")
public class Order { ... }
```

**.NET — Global Query Filter:**
```csharp
builder.HasQueryFilter(o => o.DeletedAt == null);
```

---

## Transaction Management

### Rules
- Keep transactions short — do DB work only, avoid calling external APIs inside a transaction
- Use `readOnly = true` / `AsNoTracking()` for read-only operations
- Set explicit isolation levels only when needed (default `READ COMMITTED` is usually fine)
- Handle optimistic concurrency with version columns

### Spring Boot — @Transactional

```java
// Read-only: no dirty checking, better performance
@Transactional(readOnly = true)
public OrderResponse findById(UUID id) {
    return orderRepository.findById(id)
        .map(OrderResponse::from)
        .orElseThrow(() -> new OrderNotFoundException(id));
}

// Write: default isolation, rolls back on RuntimeException
@Transactional
public OrderResponse create(CreateOrderRequest request) {
    Order order = Order.create(request);
    return OrderResponse.from(orderRepository.save(order));
}

// Explicit rollback rules
@Transactional(rollbackFor = PaymentException.class, noRollbackFor = NotificationException.class)
public void processPayment(UUID orderId) { ... }
```

**Anti-patterns:**
- Never use `@Transactional` at class level — be explicit per method
- Never call a `@Transactional` method from another method in the same class (proxy bypass)
- Never make external HTTP calls inside a transaction

### .NET — EF Core Unit of Work

```csharp
// The DbContext IS the Unit of Work — call SaveChangesAsync once at the end
public async Task<OrderResponse> Handle(CreateOrderCommand request, CancellationToken ct)
{
    var order = Order.Create(request);
    await _context.Orders.AddAsync(order, ct);
    await _context.SaveChangesAsync(ct); // Single commit point
    return OrderResponse.FromDomain(order);
}

// Explicit transaction for multi-step operations
await using var transaction = await _context.Database.BeginTransactionAsync(ct);
try
{
    await _context.Orders.AddAsync(order, ct);
    await _context.SaveChangesAsync(ct);

    await _context.Payments.AddAsync(payment, ct);
    await _context.SaveChangesAsync(ct);

    await transaction.CommitAsync(ct);
}
catch
{
    await transaction.RollbackAsync(ct);
    throw;
}
```

### Optimistic Concurrency

Use a version column to prevent lost updates.

**SQL:**
```sql
ALTER TABLE orders ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
```

**Spring Boot:**
```java
@Version
private Integer version;
```

**.NET:**
```csharp
builder.Property(o => o.Version).IsConcurrencyToken();
// Or use xmin for PostgreSQL:
builder.UseXminAsConcurrencyToken();
```

---

## Performance Rules

- Always add indexes on FK columns and columns used in `WHERE` / `ORDER BY`
- Use `EXPLAIN ANALYZE` to verify query plans on complex queries
- Use `readOnly = true` / `AsNoTracking()` for read-only queries
- Batch inserts when inserting many rows
- Never `SELECT *` in projections — always specify columns
- Never load a collection just to call `.size()` / `.Count` — use `COUNT` query
- Never use `LIKE '%term%'` on large tables — use full-text search

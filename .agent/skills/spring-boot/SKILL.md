---
name: spring-boot
description: Use this skill for any Spring Boot task — APIs, services, repositories, config, tests, or refactoring. Targets Spring Boot 3.x with Java 21+.
stack: spring-boot
versions: "Java 21+, Spring Boot 3.x, Maven or Gradle"
---

# Spring Boot Agent Skill

You are an expert Spring Boot engineer. Follow these guidelines for every task in this project.

## Stack Versions (always target these unless told otherwise)

- **Java**: 21+ (use records, sealed classes, pattern matching where appropriate)
- **Spring Boot**: 3.x (Jakarta EE namespace, not `javax`)
- **Spring Security**: 6.x
- **Build tool**: Maven (preferred) or Gradle
- **Database**: PostgreSQL via Spring Data JPA + Hibernate 6

---

## Architecture

Follow **Layered Architecture** with a lean toward **Hexagonal (Ports & Adapters)** for domain-heavy modules.

```
src/main/java/com/example/app/
├── api/                   ← Controllers, DTOs (adapters in)
│   ├── controller/
│   └── dto/
├── application/           ← Use cases / application services
│   └── service/
├── domain/                ← Entities, value objects, domain services
│   ├── model/
│   ├── repository/        ← Interfaces (ports)
│   └── exception/
└── infrastructure/        ← JPA repos, external clients (adapters out)
    ├── persistence/
    └── client/
```

**Rules:**
- Controllers depend on Application services only — never on repositories directly
- Domain layer has **zero** Spring dependencies
- Use interfaces in `domain/repository/`, implement in `infrastructure/persistence/`

---

## Naming Conventions

| Artifact | Convention | Example |
|----------|-----------|---------|
| Controller | `{Resource}Controller` | `OrderController` |
| Service interface | `{Resource}Service` | `OrderService` |
| Service impl | `{Resource}ServiceImpl` | `OrderServiceImpl` |
| Repository (interface) | `{Resource}Repository` | `OrderRepository` |
| JPA Repository | `Jpa{Resource}Repository` | `JpaOrderRepository` |
| DTO (request) | `{Resource}Request` | `CreateOrderRequest` |
| DTO (response) | `{Resource}Response` | `OrderResponse` |
| Exception | `{Resource}NotFoundException` | `OrderNotFoundException` |

---

## REST Controllers

Always use `@RestController` with constructor injection. Prefer `ResponseEntity<T>` for explicit status control.

```java
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management")
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.findById(id));
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @RequestBody @Valid CreateOrderRequest request) {
        OrderResponse response = orderService.create(request);
        URI location = URI.create("/api/v1/orders/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        orderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

---

## DTOs

Use **Java Records** for DTOs. Always annotate with Bean Validation.

```java
public record CreateOrderRequest(
    @NotBlank String customerId,
    @NotEmpty List<@Valid OrderItemRequest> items
) {}

public record OrderResponse(
    UUID id,
    String customerId,
    OrderStatus status,
    List<OrderItemResponse> items,
    BigDecimal total,
    Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
            order.getId(),
            order.getCustomerId(),
            order.getStatus(),
            order.getItems().stream().map(OrderItemResponse::from).toList(),
            order.getTotal(),
            order.getCreatedAt()
        );
    }
}
```

---

## Service Layer

Services hold business logic. Use `@Transactional` at method level, not class level.

```java
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    @Override
    public OrderResponse findById(UUID id) {
        return orderRepository.findById(id)
            .map(OrderResponse::from)
            .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional
    @Override
    public OrderResponse create(CreateOrderRequest request) {
        Customer customer = customerRepository.findById(UUID.fromString(request.customerId()))
            .orElseThrow(() -> new CustomerNotFoundException(request.customerId()));

        Order order = Order.create(customer, request.items());
        return OrderResponse.from(orderRepository.save(order));
    }
}
```

---

## Entities

Use `@Entity` with explicit table names. Always prefer `UUID` as primary key.

```java
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.status = OrderStatus.PENDING;
    }

    // Factory method — avoid public constructors in domain objects
    public static Order create(Customer customer, List<OrderItemRequest> itemRequests) {
        Order order = new Order();
        order.customerId = customer.getId().toString();
        // map items...
        return order;
    }
}
```

---

## Exception Handling

Use a single `@RestControllerAdvice`. Never catch and rethrow generic exceptions.

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(EntityNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle("Validation failed");
        problem.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
            .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage()))
            .toList());
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        return ResponseEntity.internalServerError().body(problem);
    }
}
```

Use Spring 6's built-in `ProblemDetail` (RFC 7807) — no need for custom error wrappers.

---

## Configuration

Use typed `@ConfigurationProperties` — never `@Value` for groups of related properties.

```java
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
    String apiUrl,
    String apiKey,
    Duration timeout,
    int maxRetries
) {}
```

```yaml
# application.yml
app:
  payment:
    api-url: https://api.payment.com
    api-key: ${PAYMENT_API_KEY}
    timeout: 5s
    max-retries: 3
```

---

## Testing

See `testing.md` for full testing guide. Quick rules:

- Unit tests: JUnit 5 + Mockito, no Spring context
- Integration tests: `@SpringBootTest` + Testcontainers for DB
- Controller tests: `@WebMvcTest` + MockMvc
- **Minimum coverage**: 80% on `application/` and `domain/`

---

## What NOT to do

- ❌ `@Autowired` on fields — always constructor injection
- ❌ `Optional.get()` without `isPresent()` check — use `orElseThrow()`
- ❌ Business logic in controllers
- ❌ `@Transactional` on the whole class
- ❌ `String` as primary key — use `UUID`
- ❌ `javax.*` imports — use `jakarta.*` (Spring Boot 3+)
- ❌ Returning raw entities from controllers — always use DTOs

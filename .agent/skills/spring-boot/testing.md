---
name: spring-boot-testing
description: Testing guide for Spring Boot projects. Unit tests, integration tests, controller slice tests, and Testcontainers setup.
stack: spring-boot
versions: "Java 21+, Spring Boot 3.x, JUnit 5, Testcontainers"
---

# Spring Boot Testing

## Dependencies (add to pom.xml)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Unit Tests — Service Layer

No Spring context. Fast, isolated. Mock all dependencies.

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void findById_shouldReturnOrder_whenExists() {
        // Arrange
        UUID id = UUID.randomUUID();
        Order order = OrderFixture.anyOrder(id);
        given(orderRepository.findById(id)).willReturn(Optional.of(order));

        // Act
        OrderResponse response = orderService.findById(id);

        // Assert
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void findById_shouldThrow_whenNotFound() {
        UUID id = UUID.randomUUID();
        given(orderRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findById(id))
            .isInstanceOf(OrderNotFoundException.class)
            .hasMessageContaining(id.toString());
    }
}
```

---

## Controller Tests — @WebMvcTest

Loads only the web layer. Mock the service.

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @Test
    void getById_shouldReturn200_whenOrderExists() throws Exception {
        UUID id = UUID.randomUUID();
        OrderResponse response = OrderFixture.anyOrderResponse(id);
        given(orderService.findById(id)).willReturn(response);

        mockMvc.perform(get("/api/v1/orders/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void create_shouldReturn422_whenBodyInvalid() throws Exception {
        CreateOrderRequest invalid = new CreateOrderRequest("", List.of());

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors").isArray());
    }
}
```

---

## Integration Tests — Testcontainers

Full Spring context with real database.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class OrderIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    void createOrder_shouldPersistAndReturn201() {
        CreateOrderRequest request = new CreateOrderRequest(
            "customer-123",
            List.of(new OrderItemRequest("product-1", 2))
        );

        ResponseEntity<OrderResponse> response = restTemplate.postForEntity(
            "/api/v1/orders", request, OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(orderRepository.findById(response.getBody().id())).isPresent();
    }
}
```

Note: `@ServiceConnection` (Spring Boot 3.1+) auto-configures the datasource from the container — no manual property override needed.

---

## Test Fixtures

Create a `*Fixture` or `*Mother` class per domain object to avoid repeated test setup.

```java
public class OrderFixture {

    public static Order anyOrder() {
        return anyOrder(UUID.randomUUID());
    }

    public static Order anyOrder(UUID id) {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", id);
        ReflectionTestUtils.setField(order, "customerId", "customer-test");
        ReflectionTestUtils.setField(order, "status", OrderStatus.PENDING);
        ReflectionTestUtils.setField(order, "createdAt", Instant.now());
        return order;
    }

    public static OrderResponse anyOrderResponse(UUID id) {
        return new OrderResponse(id, "customer-test", OrderStatus.PENDING, List.of(), BigDecimal.TEN, Instant.now());
    }
}
```

---

## Naming Convention for Tests

```
methodName_shouldExpectedBehavior_whenCondition

Examples:
  findById_shouldReturnOrder_whenExists
  create_shouldThrowException_whenCustomerNotFound
  delete_shouldReturn204_whenOrderDeleted
```

---

## Test Configuration

```yaml
# src/test/resources/application-test.yml
spring:
  jpa:
    show-sql: false
  flyway:
    enabled: true   # run migrations in tests too
logging:
  level:
    org.springframework: WARN
    com.example: DEBUG
```

---
name: refactor
description: Use this skill when the user asks for @refactor or wants to restructure existing code to align with project skills and conventions without changing external behavior.
stack: shared
versions: "SDD 1.x"
---

# Refactor — Structured Code Refactoring

Use this skill when the user asks for `@refactor` or wants to improve existing code structure.
This is an **independent skill** — it can be used at any time.

## Trigger phrases

- `@refactor`
- "refactor this code"
- "restructure this module"
- "align code with conventions"
- "clean up this class"

## Goal

Restructure existing code to align with project skills and conventions, improving maintainability and clarity **without changing external behavior**.

## Workflow

1. **Read project context** — load `.agent/SKILLS.md` and all linked skills.
2. **Analyze the target code** — identify what needs refactoring:
   - Architecture violations (wrong layer, wrong dependency direction).
   - Naming violations (classes, methods, database columns).
   - Missing patterns (no validation, no error handling, no logging).
   - Code smells (god classes, long methods, feature envy).
3. **Plan the refactoring** — list changes in order of execution:
   - Ensure each step preserves external behavior.
   - Identify dependencies between changes.
4. **Execute refactoring** — apply changes incrementally:
   a. One concern per commit.
   b. Run tests after each step to confirm nothing breaks.
5. **Verify** — run the full test suite, build, and lint.

## Common refactoring patterns

### Layer separation (from SKILL.md)

```text
BEFORE: Controller contains business logic and database queries
AFTER:  Controller → Service → Repository (each in correct package/namespace)
```

### Naming alignment (from conventions.md)

```text
BEFORE: OrderDTO, OrderSvc, OrderRepo
AFTER:  OrderResponse, OrderService, OrderRepository
```

### Error handling (from error-handling.md)

```text
BEFORE: catch (Exception e) { return ResponseEntity.status(500).body(e.getMessage()); }
AFTER:  throw new NotFoundException("Order", orderId);  // handled by global @RestControllerAdvice
```

### Database patterns (from database.md)

```text
BEFORE: SELECT * FROM orders WHERE customer = '...'  (string concat, SELECT *)
AFTER:  @Query("SELECT o FROM Order o WHERE o.customerId = :customerId")  (parameterized, projection)
```

### Observability (from observability.md)

```text
BEFORE: System.out.println("Order created: " + order.getId());
AFTER:  log.info("Order created", Map.of("orderId", order.getId(), "customerId", order.getCustomerId()));
```

## Quick command

```text
@refactor
Refactor the Order module to follow skills/spring-boot/SKILL.md layer separation.
```

```text
@refactor
Align error handling in src/main/java/com/example/api/ with skills/shared/error-handling/SKILL.md.
```

## Refactoring checklist

```text
✓ Read all relevant skills before starting
✓ Existing tests pass before refactoring
✓ Each change preserves external behavior
✓ One concern per commit
✓ Tests still pass after each step
✓ No new features added (refactoring only)
✓ Build and lint pass at the end
```

## Output quality rules

- Always preserve external behavior — refactoring must not change what the code does.
- Reference the specific skill driving each change.
- Run tests after each step, not just at the end.
- Keep commits atomic — one refactoring concern per commit.
- If tests are missing for the code being refactored, write them first (using `@test`).

## What NOT to do

- ❌ Do not refactor without running existing tests first — you need a safety net.
- ❌ Do not change external behavior — that is a feature change, not a refactoring.
- ❌ Do not refactor and add features in the same commit.
- ❌ Do not ignore failing tests after refactoring — they indicate broken behavior.
- ❌ Do not refactor code without reading the relevant skills — changes must align with conventions.
- ❌ Do not do large "big bang" refactors — work incrementally, commit frequently.

---
name: test-suite
description: >
  Step-by-step guide to generate a test suite for a component,
  following the architecture contract's test strategy.
artifact: test classes
---

# Test Suite Prompt

Generate tests following the project's testing conventions and the architecture contract's test strategy.

## Required Inputs

| Input | Description |
|-------|-------------|
| `target` | The class being tested |
| `type` | unit · integration · controller-slice |
| `scenarios` | List of test cases from the test strategy |

## Steps

### 1. Analyze existing tests

Find 3+ existing tests **of the same type** (unit, integration, or slice). Note:
- Test class naming convention
- Method naming convention
- Setup pattern (@BeforeEach, fixtures, builders)
- Assertion library (AssertJ, Hamcrest, FluentAssertions, plain JUnit)
- Mock framework (Mockito, NSubstitute, manual fakes)
- Data setup approach (fixtures, builders, random generators)

### 2. Create test class

- Follow discovered naming convention exactly
- Use same annotations and base class (if any)
- Set up mocks/dependencies matching existing patterns

### 3. Implement test methods

For each scenario in the test strategy:
- Use **Arrange / Act / Assert** structure
- Name following the project's test naming convention
- Use the existing assertion library
- Mock only what existing tests mock (don't over-mock)

### 4. For integration tests

- Use the project's integration test setup (Testcontainers, in-memory DB, etc.)
- Follow existing test configuration (base class, profiles)
- Use realistic test data, not trivial values

### 5. Verify

- [ ] Only scenarios from the test strategy are covered
- [ ] Naming matches existing tests
- [ ] Same assertion library and mock framework as existing tests
- [ ] No new test dependencies introduced
- [ ] Tests are independent (no shared mutable state)
- [ ] No extra "defensive" tests beyond the strategy

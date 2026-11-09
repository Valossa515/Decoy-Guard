# Action Prompts

Reusable, step-by-step prompt templates for common code generation tasks. Each prompt defines the exact inputs, steps, and verification checklist for a specific artifact.

These prompts are used by the **builder** and **tester** agents, but can also be invoked directly by a developer.

## Available Prompts

| Prompt | Description | Used by |
|--------|-------------|---------|
| [endpoint.md](endpoint.md) | Generate a REST endpoint (controller + DTOs) | builder |
| [entity.md](entity.md) | Generate a domain entity with invariants | builder |
| [service.md](service.md) | Generate an application service / use case | builder |
| [repository-impl.md](repository-impl.md) | Generate a repository implementation | builder |
| [test-suite.md](test-suite.md) | Generate a test suite for a component | tester |

## Usage

Reference a prompt when asking the agent to generate a specific artifact:

> "Follow the prompt in `.agent/prompts/endpoint.md` to create a GET /api/v1/orders/{id} endpoint"

Each prompt automatically loads the active skills to ensure naming, architecture, and testing conventions are followed.

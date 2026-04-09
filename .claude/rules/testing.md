# Testing

Tests are part of the product. Placeholder tests, empty suites and fake `test` scripts are treated as defects, not temporary shortcuts.

---

## Core principles

- If code contains business logic, auth rules, validation, orchestration or scan logic, it needs automated tests.
- A test command must run real tests. `echo "No tests configured"` is not an acceptable steady state for active modules.
- Prefer a small number of trustworthy tests over large brittle suites.
- New behavior must come with tests in the same change unless the module is explicitly documented as a placeholder.
- Missing tests on security-sensitive code are review blockers.

---

## Repository-aligned conventions

### Backend

- Use JUnit 5 and Mockito by default.
- Mirror the production package under `src/test/java/com/virtualrift/...` unless there is a strong reason not to.
- Prefer `@Nested` and `@DisplayName` for readability when a class has multiple behaviors.
- Test method names should follow `method_scenario_expectedResult`.
- Keep helper builders and fixtures inside the test package or dedicated test utilities.

### Frontend

- Use Vitest for unit and package tests.
- Use React Testing Library for component behavior.
- Use `msw` for network mocking instead of stubbing `fetch` ad hoc.
- Place component tests next to the component or under a nearby `__tests__/` folder.
- Shared packages under `frontend/packages/` must expose real `test` scripts once they contain source code.

---

## Hardening rules

- Cover both happy path and negative path for each critical behavior.
- Assert observable behavior and side effects, not implementation trivia.
- Prefer explicit exception types, HTTP status codes, event payloads and state transitions over vague `assertNotNull(...)` only.
- Use `verify(...)` for important side effects such as denylist writes, quota checks, event publication and notification dispatch.
- Avoid `Thread.sleep()`; use deterministic async coordination.
- Avoid Mockito leniency by default. If `lenient()` or `Strictness.LENIENT` is required, document why in the test.
- Do not leave empty test files, TODO-only tests or `expect(true).toBe(false)` placeholders in the repository.

---

## What must always be tested

Regardless of coverage tooling, these scenarios are mandatory:

- authentication, authorization and token lifecycle rules
- tenant isolation boundaries and cross-tenant rejection paths
- scan target validation, especially internal network and metadata blocking
- rate limiting behavior, including `429` and `Retry-After`
- RFC 7807 error formatting for public APIs
- Kafka or async event publication for orchestrated flows
- scanner detection and false-positive resistance
- report and evidence masking when sensitive output is involved

---

## Recommended test mix by module type

### Services and domain logic

- Unit tests for validation, branching logic and edge cases
- Focused integration tests where persistence, Redis, Kafka or HTTP contracts matter

### Gateway and security boundaries

- Unit tests for filters, token parsing and deny/allow decisions
- Integration tests for auth chains, rate limiting and error shaping where practical

### Scanners

- Rule-level tests for vulnerable and clean inputs
- Regression tests for known payloads and precision cases
- Integration tests against controlled targets where the cost is justified
- Explicit tests that internal targets are rejected before any outbound action

### Frontend packages

- API client tests for auth headers, retries, RFC 7807 parsing and error mapping
- Shared types or helpers tested as real runtime helpers when logic exists
- Component tests only when the module contains actual UI behavior

---

## Integration test guidance

- Use `@SpringBootTest` only for boundaries that need the framework wiring.
- Use Testcontainers for database, Redis, Kafka or service dependencies when contract fidelity matters.
- Use WireMock or `msw` for external HTTP systems.
- Keep integration tests focused on one contract or flow each.
- Integration tests must clean up after themselves and must not depend on order.

---

## What not to spend time testing

- framework internals
- Lombok-generated accessors or trivial records
- third-party library behavior already guaranteed upstream
- static configuration classes with no conditional logic

---

## Pull request gates

- No pull request may merge with failing or placeholder tests.
- New code in active modules must be covered by tests appropriate to its risk.
- If an active module has no real test runner configured, adding one is part of the work.
- Coverage thresholds are useful only when the instrumentation is real; do not claim enforced coverage until CI actually enforces it.
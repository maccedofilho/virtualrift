# Code Style

Consistency is important, but so is correctness. These standards favor readable, testable and security-aware code.

---

## General rules

- Prefer clarity over cleverness.
- Use one source of truth for versions and shared configuration where the toolchain allows it.
- Do not commit backup files such as `*.bak`, `*.tmp`, `*.orig` or copied source snapshots.
- If a module is only a placeholder, document it as such instead of pretending it is production-ready.
- If a script says `test` or `lint`, it must execute the real tool, not a placeholder echo.

---

## Java

### Naming

- Classes and records: `PascalCase`
- Methods, fields and local variables: `camelCase`
- Constants: `UPPER_SNAKE_CASE`
- Packages: lowercase and stable, following the module naming already used in the repository
- Avoid abbreviations unless they are well-known domain terms such as `JWT`, `URL` or `API`

### Structure

- One top-level class or record per file
- Prefer constructor injection; do not use field injection
- Keep member order predictable: constants, fields, constructors, public methods, private helpers
- Keep orchestration logic out of controllers
- Cross-module communication should be explicit through APIs, events or orchestrator flows, not hidden service coupling

### Defensive coding

- Catch specific exceptions only; do not catch `Exception` or `Throwable` unless you immediately wrap and preserve context at a boundary
- Never swallow exceptions silently
- Prefer value objects, records and explicit domain types over loose `Map<String, Object>` structures
- Prefer `Optional` over returning `null` from repository-like APIs when it improves clarity
- Validate external input before it reaches domain logic or dangerous sinks

---

## React and TypeScript

### Components and hooks

- Use functional components only
- Type props explicitly
- Avoid `any`; use `unknown` and narrow
- Do not fetch directly inside presentation components when a shared API client or hook is appropriate
- Keep hooks focused on one responsibility

### Shared boundaries

- Shared runtime types belong in `packages/virtualrift-types`
- Shared API access belongs in `packages/virtualrift-api-client`
- Do not import from one app directly into another app
- Do not store auth tokens in browser storage as a convenience shortcut

---

## Formatting and imports

- Use 4 spaces in Java and 2 spaces in TypeScript/TSX
- Keep lines readable; break long expressions instead of compressing them
- No wildcard imports in Java
- Group imports consistently: standard library, third-party, internal
- Do not leave trailing whitespace or malformed formatting for later

---

## Tests and fixtures

- Test code should be readable production code, not a dumping ground
- Prefer explicit fixtures over deeply nested mock setup
- Avoid Mockito leniency unless the test documents why strict stubbing is not practical
- Keep helper methods close to the tests that use them
- Empty test files and failing placeholder assertions are style violations as well as quality defects

---

## Documentation

- Add Javadoc or JSDoc when the public contract is not obvious from the name and types
- Document invariants, side effects and security-relevant assumptions
- Keep comments focused on intent and constraints, not on narrating syntax
- If README, commands or skills become inaccurate after a change, update them in the same workstream

---

## Dependencies and manifests

- Prefer centrally managed versions for shared Java dependencies
- Use lockfiles for JS workspaces and declare the package manager explicitly
- Open version ranges require justification; reproducibility beats convenience
- Add only the dependencies a module directly uses

---

## Commits

VirtualRift uses Conventional Commits:

```text
<type>(scope): short description
```

Preferred types:

- `feat`
- `fix`
- `refactor`
- `test`
- `docs`
- `chore`
- `security`

Commit messages should explain the reason for the change, not just list touched files.
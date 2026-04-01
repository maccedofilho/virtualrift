# Code Style

All VirtualRift contributors must follow these standards. Consistency across the codebase is non-negotiable.

---

## Java

### Naming
- Classes: `PascalCase` — `ScanOrchestrator`, `VulnerabilityReport`
- Methods and variables: `camelCase` — `findByTenantId()`, `scanResult`
- Constants: `UPPER_SNAKE_CASE` — `MAX_SCAN_TIMEOUT`
- Packages: lowercase, no underscores — `com.virtualrift.scanner.web`
- No abbreviations — `vulnerability` not `vuln`, `scanner` not `scnr`

### Structure
- One class per file, always
- Class member order: constants → fields → constructors → public methods → private methods
- Maximum 1 level of nesting inside lambdas and streams — extract to a named method if deeper
- Services must never call other services directly — communicate via events (Kafka) or the orchestrator
- Never use `@Autowired` on fields — always constructor injection

### Best Practices
- All public methods in service classes must have a corresponding unit test
- Never catch `Exception` or `Throwable` — catch specific exceptions only
- Never swallow exceptions with an empty catch block
- Use `Optional` instead of returning `null`
- Prefer records for DTOs: `public record ScanResultDto(UUID id, String status) {}`
- No business logic in controllers — controllers only validate input and delegate to services

---

## React + TypeScript

### Components
- One component per file, filename matches the component name: `ScanResultCard.tsx`
- Use functional components only — no class components
- Props must always be typed with an explicit interface: `interface ScanResultCardProps {}`
- Never use `any` — if the type is unknown, use `unknown` and narrow it
- Component files live in `src/components/` grouped by feature, not by type

### Hooks
- Custom hooks must start with `use`: `useScanResults`, `useTenantContext`
- One responsibility per hook — if it's doing too much, split it
- Never fetch data directly inside components — always use a custom hook or React Query

### Types
- Shared types live in `packages/virtualrift-types`
- Prefer `type` over `interface` for unions and intersections
- Enums must be string enums: `enum ScanStatus { PENDING = 'PENDING', RUNNING = 'RUNNING' }`
- Never use non-null assertion (`!`) — handle the nullability explicitly

---

## Formatting

- Indentation: 2 spaces for TypeScript/React, 4 spaces for Java
- Maximum line length: 120 characters (Java), 100 characters (TypeScript)
- Always use trailing commas in TypeScript multiline structures
- No trailing whitespace, no blank lines at end of file
- Java: opening brace on the same line, never on a new line
- Formatting is enforced automatically — Java via Checkstyle, TypeScript via ESLint + Prettier

---

## Imports and Dependencies

### Java
- No wildcard imports (`import com.virtualrift.*` is forbidden)
- Import order: Java standard library → third-party → internal (`com.virtualrift`)
- Never add a dependency without discussing with the team first
- Each module must declare only the dependencies it directly uses — no transitive dependency abuse

### TypeScript
- Absolute imports only using path aliases: `@virtualrift/ui`, `@/components/ScanCard`
- No relative imports that go more than 1 level up: `../../utils` is forbidden
- Never import from another app directly — use the shared packages only

---

## Documentation

### Javadoc
- Required on all public classes and public methods in `service/` and `domain/` layers
- Must describe **what** and **why**, not **how**
- Include `@param`, `@return` and `@throws` when applicable
- Controllers do not need Javadoc — they are documented via OpenAPI annotations
```java
/**
 * Triggers a new vulnerability scan for the given target.
 * Publishes a scan.started event to Kafka after persisting the scan record.
 *
 * @param request scan configuration including target URL and scan type
 * @param tenantId extracted from the JWT, used for isolation
 * @return the created scan with its initial status
 * @throws TenantQuotaExceededException if the tenant has reached their scan limit
 */
public ScanResponse triggerScan(ScanRequest request, UUID tenantId) {}
```

### JSDoc
- Required on all exported functions and hooks in shared packages
- Components do not need JSDoc if the props interface is well named and typed
- Keep it short — one line is enough if the name is self-explanatory
```typescript
/**
 * Fetches paginated scan results for the current tenant.
 * Automatically re-fetches when a scan.completed event is received.
 */
export function useScanResults(filters: ScanFilters): ScanResultsQuery {}
```

---

## Commits

VirtualRift uses Conventional Commits. Every commit message must follow this format:
```
<type>(scope): short description

optional body explaining the why
```

### Types
| Type | When to use |
|---|---|
| `feat` | New feature |
| `fix` | Bug fix |
| `refactor` | Code change with no feature or fix |
| `test` | Adding or fixing tests |
| `docs` | Documentation only |
| `chore` | Build, deps, config changes |
| `security` | Security-related fix or hardening |

### Examples
```
feat(web-scanner): add XSS detection via DOM-based analysis
fix(auth): prevent token reuse after logout
security(gateway): enforce rate limiting per tenant on scan endpoints
refactor(orchestrator): extract scan partitioner into dedicated class
```

- Subject line: max 72 characters, imperative mood, no period at the end
- Breaking changes must include `BREAKING CHANGE:` in the commit body
- Reference issues when applicable: `closes #142`
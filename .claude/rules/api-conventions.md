# API Conventions

All VirtualRift APIs must be predictable, tenant-safe and explicit about failures.

---

## Versioning and lifecycle

- Prefix public APIs with `/api/v{n}/`.
- Introduce a new version only for breaking contract changes.
- Deprecated APIs must communicate that status explicitly and document the migration path.
- Health and actuator routes are operational exceptions and must remain intentionally scoped.

---

## Authentication and tenant scope

- All non-public endpoints require a bearer token.
- The token is carried in `Authorization: Bearer <token>`.
- Tenant context comes from the validated token, never from request body or query string.
- Avoid putting `tenantId` in public API paths unless the endpoint is an explicit admin capability with stronger authorization and audit requirements.
- Public auth endpoints must still validate input aggressively and avoid leaking whether an account exists.

---

## Resource design

- Prefer plural nouns and kebab-case for resource paths.
- Action-style endpoints are acceptable only for protocol actions such as auth or export workflows where a pure resource model would be misleading.
- Keep nesting shallow. If a route needs more than two resource levels, reconsider the design.
- Query parameters should use camelCase and remain stable over time.
- Response bodies should use camelCase consistently.

---

## Request and response rules

- Use explicit request DTOs; do not bind arbitrary payloads into privileged models.
- For create and update flows, validate every writable field and reject unknown or unsupported input where the framework allows it.
- Mutation endpoints that may be retried by clients should support idempotency when duplicate execution is harmful.
- Include correlation metadata such as `X-Request-Id` when tracing across services is important.

---

## Error format

Use RFC 7807 for non-success responses:

```json
{
  "type": "https://virtualrift.io/errors/scan-not-found",
  "title": "Scan not found",
  "status": 404,
  "detail": "No scan found with the supplied identifier.",
  "instance": "/api/v1/scans/3fa85f64-5717-4562-b3fc-2c963f66afa6"
}
```

- `type` must be stable and machine-meaningful.
- `title` should be short and human-readable.
- `detail` must help the caller fix the request without exposing internals.
- Never include stack traces, SQL fragments, filesystem paths, hostnames or sensitive tenant data.
- Validation errors should include an `errors` array with field-level issues.

---

## Pagination, filtering and sorting

List endpoints should support a predictable envelope:

```json
{
  "data": [],
  "pagination": {
    "page": 0,
    "pageSize": 20,
    "totalElements": 143,
    "totalPages": 8
  }
}
```

- Default `pageSize` should be conservative and capped.
- Filters belong in query parameters.
- Date and time filters must use ISO 8601.
- Sorting should be explicit, stable and limited to approved fields.

---

## Rate limiting and abuse controls

- Rate limits apply per tenant and, where needed, per action type.
- When a limit is exceeded, return `429 Too Many Requests` with `Retry-After`.
- Expose standard rate-limit headers when practical:

```text
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 743
X-RateLimit-Reset: 1714521600
```

- Scan-triggering endpoints should use stricter limits than read-only endpoints.

---

## Scan-specific API rules

- Scan target input must be normalized and validated before scheduling.
- Reject internal, metadata or disallowed network targets before any outbound work starts.
- Do not accept raw scanner flags or raw shell arguments from API consumers.
- Scan findings and reports returned by APIs must be tenant-scoped and masked where sensitive evidence exists.

---

## Frontend contract expectations

- Frontend clients should consume the shared API client package instead of duplicating retry and auth behavior.
- Public APIs should remain stable enough to support typed clients and shared contract tests.
- Changes to auth headers, pagination envelopes, problem details or scan payloads require coordinated test updates.
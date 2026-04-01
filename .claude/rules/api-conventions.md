# API Conventions

All VirtualRift services must follow these conventions to ensure consistency across the platform.

---

## Versioning

- All endpoints must be prefixed with the API version: `/api/v1/`, `/api/v2/`
- A new version must be created when a breaking change is introduced
- Old versions must remain functional for at least 6 months after deprecation
- Deprecation must be communicated via the `Deprecation` and `Sunset` response headers

---

## Authentication

- All endpoints (except `/health` and `/auth/token`) require a Bearer token
- Token must be sent in the `Authorization` header: `Authorization: Bearer <token>`
- Tokens are JWT signed with RS256 and include `tenant_id`, `user_id`, `roles` and `exp`
- Services must never trust user-supplied `tenant_id` from the request body — always extract from the token

---

## Naming Conventions

- URLs must use **kebab-case** and **plural nouns**: `/api/v1/scan-results`, `/api/v1/vulnerability-reports`
- No verbs in URLs — use HTTP methods to express the action
- Nested resources are allowed up to 2 levels: `/api/v1/tenants/{tenantId}/scans`
- Query parameters must use **camelCase**: `?pageSize=10&sortBy=createdAt`
- Response body fields must use **camelCase**

### HTTP Methods
| Action | Method |
|---|---|
| List | GET |
| Get one | GET /{id} |
| Create | POST |
| Full update | PUT /{id} |
| Partial update | PATCH /{id} |
| Delete | DELETE /{id} |

---

## Error Format (RFC 7807)

All error responses must follow the Problem Details standard:
```json
{
  "type": "https://virtualrift.io/errors/scan-not-found",
  "title": "Scan not found",
  "status": 404,
  "detail": "No scan found with id 3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "instance": "/api/v1/scans/3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "tenantId": "acme-corp"
}
```

- `type` must be a stable URI identifying the error category
- `detail` must be human-readable and actionable
- Never expose stack traces, internal paths or database errors in responses
- Validation errors must list all invalid fields in an `errors` array

---

## Pagination and Filters

All list endpoints must support pagination via query parameters:
```
GET /api/v1/scans?page=0&pageSize=20&sortBy=createdAt&sortDir=desc
```

Response envelope:
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

- Default `pageSize` is 20, maximum is 100
- Filters must use query parameters: `?status=completed&severity=HIGH`
- Date filters must use ISO 8601: `?createdAfter=2024-01-01T00:00:00Z`

---

## Rate Limiting

All responses must include rate limit headers:
```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 743
X-RateLimit-Reset: 1714521600
```

- Limits are applied per `tenant_id`
- When the limit is exceeded, respond with `429 Too Many Requests` and a `Retry-After` header
- Scan trigger endpoints (`POST /scans`) have a separate lower limit defined per plan
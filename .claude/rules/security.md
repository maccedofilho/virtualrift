# Security Rules

These rules are mandatory for all VirtualRift services. Security is a first-class concern — every contributor is responsible for enforcing these standards.

---

## Authentication and Authorization

- Every endpoint must verify the JWT signature before processing any request — no exceptions
- Always extract `tenant_id`, `user_id` and `roles` from the validated token — never from request body or query params
- Role checks must happen at the service layer, not only at the controller layer
- Use Spring Security's `@PreAuthorize` for method-level authorization
- Tokens must have a maximum expiry of 15 minutes — refresh tokens max 7 days
- Invalidated tokens must be added to a Redis denylist immediately on logout

---

## Multi-Tenancy Isolation

- Every database query must include `tenant_id` as a mandatory filter — no exceptions
- Row-Level Security (RLS) must be enabled in PostgreSQL for all tenant-scoped tables
- Tenants must never share scan workers — each scan job runs in an isolated container
- Never log or expose `tenant_id` from one tenant in a context where another tenant could read it
- Cross-tenant data access must be treated as a critical security incident

---

## Input Validation

- All inputs must be validated at the controller layer using Bean Validation (`@Valid`)
- Never trust user-supplied URLs for scanning — validate format, schema (`http/https` only) and against a blocklist
- Blocklist must include: `localhost`, `127.0.0.1`, `0.0.0.0`, `169.254.0.0/16` (AWS metadata), `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`
- Never pass user input directly to shell commands, SQL queries or file paths
- File uploads must be validated by MIME type and size — never trust the file extension alone

---

## Secrets Management

- No secrets in source code, ever — no API keys, passwords, tokens or credentials in any file
- No secrets in environment variables checked into git — use `.env.example` with placeholders only
- All secrets must be stored in HashiCorp Vault and injected at runtime via Kubernetes secrets
- Rotate all secrets every 90 days — rotation must not require a redeployment
- Database passwords must be unique per environment (dev, staging, production)

---

## Scan Engine Isolation

- Each scan engine runs in its own container with a read-only filesystem
- Network access for scan containers is restricted to the target only — no access to internal VirtualRift services
- Scan containers must run as non-root with a dedicated service account
- Scan results are written to S3/MinIO only — scan containers have no direct database access
- CPU and memory limits must be enforced on every scan container via Kubernetes resource quotas
- Maximum scan duration must be enforced — kill any scan exceeding the configured timeout

---

## Sensitive Data Handling

- Never log passwords, tokens, secrets, full credit card numbers or PII
- Mask sensitive fields in logs: show only the last 4 characters of tokens and IDs when needed for debugging
- Vulnerability findings that include credentials or secrets must be encrypted at rest using AES-256
- Reports containing sensitive findings must be accessible only to users with the `REPORT_READ` role within the same tenant
- Scan targets (URLs, IPs) must be treated as sensitive data — do not expose them in error messages or public logs

---

## Dependencies

- Run `OWASP Dependency Check` on every pull request — fail the build if a CRITICAL CVE is found
- No dependency may be added without a known, maintained source and an open-source license compatible with the project
- Keep all dependencies up to date — review and update monthly
- Never use a dependency that has been abandoned for more than 12 months without explicit team approval
- Lock all dependency versions — no open ranges (`latest`, `^`, `~` are forbidden in production dependencies)

---

## Code Review Security Checklist

Every pull request touching security-sensitive areas must be reviewed against this checklist:

- [ ] No secrets or credentials in the diff
- [ ] All new endpoints are authenticated and authorized
- [ ] User inputs are validated and sanitized
- [ ] No direct SQL string concatenation
- [ ] No shell command execution with user input
- [ ] Tenant isolation is preserved in all new queries
- [ ] Error responses do not expose internal details
- [ ] New dependencies have been checked for known CVEs
- [ ] Scan engine changes preserve container isolation

---

## Incident Response

- Any suspected security breach must be reported immediately to the security channel
- Affected tenant must be notified within 24 hours of a confirmed breach
- Compromised tokens must be invalidated within 5 minutes of detection
- All security incidents must be documented with timeline, impact and remediation steps
- Post-mortems are mandatory for any incident rated HIGH or CRITICAL

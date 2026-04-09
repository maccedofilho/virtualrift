# Security Rules

These rules are mandatory for every VirtualRift change. They cover both universal application security risks and platform-specific risks for a multi-tenant scanning SaaS.

Use this file as the baseline for:

- code reviews
- architecture decisions
- build and deployment gates
- scanner design
- incident handling

---

## Threat model baseline

Every change must be evaluated against these risk classes:

- authentication and session weaknesses
- authorization failures, including BOLA/IDOR and privilege escalation
- tenant isolation failures and lateral movement
- injection classes: SQL, NoSQL, LDAP, template, command, path traversal and insecure deserialization
- web and API flaws: XSS, CSRF, SSRF, open redirect, CORS mistakes, mass assignment and unsafe file upload
- secrets exposure and weak cryptography
- sensitive data leakage through logs, reports or telemetry
- denial of service, abusive automation and quota bypass
- dependency, build and supply-chain compromise
- container, Kubernetes, cloud and CI/CD misconfiguration
- scanner abuse, including targeting internal VirtualRift infrastructure

If a change introduces or expands one of these classes, the change is security-sensitive by default.

---

## Identity, sessions and tokens

- Every non-public endpoint must verify JWT signature, expiry and intended algorithm before processing the request.
- Public routes must be explicitly listed; there is no implicit public access.
- Always extract `tenant_id`, `user_id` and `roles` from the validated token, never from request body, path or query parameters.
- Role checks must exist at the service layer for sensitive actions; controller-only protection is not enough.
- Access tokens must be short lived. Refresh tokens must be rotatable, revocable and invalidated on logout or compromise.
- Token identifiers used for logout or revocation must be added to the denylist immediately.
- Never log raw tokens, password reset links, session identifiers or one-time codes.
- Frontend code must not persist bearer tokens in `localStorage` or `sessionStorage`.

---

## Authorization and tenant isolation

- Every tenant-scoped read and write must be constrained by `tenant_id`.
- Cross-tenant access is always a `CRITICAL` defect unless the code is a deliberate admin-only control with explicit audit logging.
- Repository and query methods must never default to unrestricted `findAll()` semantics for tenant data.
- RLS is required for tenant-scoped PostgreSQL tables where the architecture depends on database isolation.
- Kafka messages and async jobs must preserve tenant context and validate it before processing.
- Reports, scan findings, quotas, targets and tokens must never be readable across tenants.
- Do not expose another tenant's identifiers, targets, URLs, IPs or scan metadata in logs, metrics, error messages or UI.

---

## Input validation and injection defense

- Validate all external input at the boundary using the framework's validation primitives and domain checks.
- Never concatenate user input into SQL, shell commands, file paths, URLs or templates.
- Treat file paths, archive extraction and upload destinations as hostile input.
- File uploads must validate MIME type, size and destination handling server-side; extension checks alone are not sufficient.
- Reject unsafe deserialization formats and untrusted object graphs.
- Never use `Runtime.exec`, `ProcessBuilder` or external tools with raw user-controlled arguments.
- Command execution that cannot be avoided must use a strict allowlist mapping from safe options to concrete arguments.
- Escape or sanitize user-controlled output rendered in HTML, Markdown, PDF or reports.

---

## Web, API and frontend security

- All APIs must follow explicit authentication, authorization and rate-limiting rules.
- Error responses must not leak stack traces, filesystem paths, SQL errors, internal hostnames or cloud metadata.
- Validation failures must be explicit and machine-readable without exposing internal implementation details.
- CORS must be allowlist-based; never use wildcard origins with credentials.
- CSRF defenses are required whenever cookie-based or browser-automated credentials are in scope.
- Do not render untrusted HTML with `dangerouslySetInnerHTML` unless content is sanitized and the exception is documented.
- All frontend API traffic must go through the shared API client layer when one exists; do not hand-roll auth headers in feature code.
- Avoid mass assignment by mapping request DTOs to explicit command objects instead of binding directly to privileged models.

---

## Scanner-specific security controls

- A scanner must never target internal VirtualRift services, metadata endpoints or RFC1918/private ranges unless there is explicit, controlled product support for that exact case.
- Scan target validation must block at least: `localhost`, `127.0.0.0/8`, `0.0.0.0/8`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `169.254.0.0/16`, link-local IPv6, cloud metadata endpoints and internal service DNS names.
- Target authorization must be checked before any outbound connection or scan scheduling occurs.
- Each scan job must run with least privilege, bounded CPU and memory, read-only filesystem where possible and enforced timeouts.
- Scanner output must be treated as hostile data until normalized, masked and classified.
- Findings containing secrets, credentials or customer source snippets must be redacted or encrypted before persistence and reporting.
- Scanners must fail closed when target validation, authorization or sandbox guarantees cannot be established.

---

## Secrets, crypto and sensitive data

- No secrets, credentials, API keys or private certificates may be committed to git, including examples with live values.
- Sensitive configuration must come from Vault or the platform secret manager, not inline literals in `application.yml`, manifests or scripts.
- Use modern, maintained cryptographic libraries only. Do not invent custom crypto.
- Passwords must be hashed with a dedicated password hashing algorithm; never with general-purpose hashing alone.
- Random values for tokens, keys or passwords must come from cryptographically secure sources.
- Logs, traces and reports must mask secrets, tokens, email addresses and target details unless there is a documented operational reason not to.
- Reports must include only the minimum evidence needed to prove a finding.

---

## Dependencies, build and supply chain

- Every new dependency needs a maintained source, a compatible license and an explicit reason to exist.
- Production dependencies, container images and scanner tools must be pinned to an auditable version; do not use `latest`.
- Java and JS dependency updates must be reviewed for known CVEs before merge.
- Commit lockfiles for package-managed workspaces so installations are reproducible.
- Declare the workspace package manager explicitly when the ecosystem supports it.
- Generated backup artifacts such as `*.bak`, `*.orig` and ad-hoc copies must not be committed.
- CI must fail on `CRITICAL` vulnerabilities and should fail on `HIGH` unless there is an approved exception.

---

## Containers, Kubernetes, cloud and deployment

- Containers must run as non-root unless there is a documented exception approved by security.
- Use read-only filesystems, minimal base images and multi-stage builds whenever possible.
- Do not inject secrets as plain environment variables when mounted secrets or runtime retrieval is available.
- Resource requests and limits are mandatory for scan workloads and long-running services.
- Network policies and security groups must follow least privilege; avoid `0.0.0.0/0` except where externally exposed ingress is explicitly required.
- Storage and databases must encrypt data at rest and avoid public exposure by default.
- Deployment plans must include rollback, smoke validation and security regression checks.

---

## Logging, observability and incident response

- Log security-relevant events with enough context to investigate without exposing secrets.
- Authentication failures, denylist events, permission denials, scan target rejections and cross-tenant access attempts must be observable.
- Any suspected cross-tenant exposure, auth bypass, secret leak or scanner escape is an immediate incident.
- Compromised tokens must be revoked quickly and incident timelines must be documented.
- High-severity incidents require a post-mortem with root cause and preventive action items.

---

## Mandatory security review checklist

Every security-sensitive change must pass this checklist:

- [ ] No secrets, credentials or private data appear in the diff.
- [ ] New or changed endpoints have explicit authn and authz behavior.
- [ ] Tenant isolation is preserved in sync and async paths.
- [ ] Inputs are validated and never reach dangerous sinks unsafely.
- [ ] Scanner changes cannot reach internal VirtualRift infrastructure.
- [ ] Error handling and reporting do not leak sensitive implementation details.
- [ ] Dependencies, images and tooling were checked for CVEs and version drift.
- [ ] Tests cover the security behavior introduced or modified by the change.

---

## Automatic blockers

Stop and escalate immediately if you find any of the following:

- committed secrets or live credentials
- unauthenticated access to tenant or report data
- cross-tenant read or write paths
- a scanner path that can reach internal infrastructure or cloud metadata
- unsafe command execution with user input
- a `CRITICAL` dependency or image vulnerability in active use

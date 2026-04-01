# Security Audit Skill

## Trigger
This skill is invoked via `/project:security-audit`.
Use it to run a full security audit on modified files before opening a pull request.

---

## When to Use

- Before opening any pull request that touches authentication, authorization or tenant isolation
- Before merging any change to scan engine code
- Before deploying to staging or production
- Whenever a dependency is added or updated

---

## Inputs

Before starting, collect the following:

1. **What changed?** Ask for the list of modified files or the git diff
2. **What area?** Identify the audit area: `Application`, `Frontend`, `Infrastructure` or `Configuration`
3. **Is this a security-sensitive change?** Authentication, tenant logic, scan engine, secrets, infrastructure

If the user does not provide a diff, run:
```bash
git diff main --name-only
```
Then ask the user to share the contents of the flagged files.

---

## Execution Steps

Follow these steps in order. Do not skip steps even if the change looks small.

### Step 1 — Map the attack surface
Identify all entry points introduced or modified by the change:
- New or modified REST endpoints
- New Kafka consumers or producers
- New file upload handlers
- New external HTTP calls
- New environment variables or configuration values
- New dependencies in `pom.xml` or `package.json`

Output a concise list of entry points found before proceeding.

### Step 2 — Run the checklist for the identified area

**Application changes** — verify:
- JWT validation is not bypassed on new endpoints
- `tenant_id` is extracted from the token, never from user input
- All inputs are annotated with `@Valid`
- No user input reaches shell execution or raw SQL
- Every new repository method filters by `tenant_id`
- Sensitive data is masked in logs
- No secrets are hardcoded

**Frontend changes** — verify:
- No secrets or tokens in client-side code
- No `dangerouslySetInnerHTML` with user content
- Sensitive data is not persisted in `localStorage` or `sessionStorage`
- All API calls go through `packages/virtualrift-api-client`

**Infrastructure changes** — verify:
- Containers run as non-root
- Resource limits are defined
- No `0.0.0.0/0` ingress except on the load balancer
- Secrets are mounted from Kubernetes secrets, not plain env vars
- No `latest` image tags
- S3 buckets have public access blocked

**Configuration changes** — verify:
- No inline secrets in `application.yml`
- All sensitive values reference environment variables
- New dependencies are checked against the NVD database for known CVEs

### Step 3 — Check dependencies
For every new or updated dependency found in Step 1:
```bash
# Java
mvn dependency-check:check

# TypeScript
npx audit --audit-level=high
```

Flag any dependency with a `HIGH` or `CRITICAL` CVE as a blocker.

### Step 4 — Simulate an attacker
For each entry point identified in Step 1, ask:

- Can an unauthenticated user reach this?
- Can a tenant access another tenant's data through this?
- Can user input be used to manipulate system behavior (injection, path traversal, SSRF)?
- Can this endpoint be abused to scan internal VirtualRift infrastructure?
- Does this expose more data than necessary?

Document any positive answer as a finding.

### Step 5 — Produce the audit report

Use the output format defined in `agents/security-auditor.md`:
```
## Audit Summary
## Findings
### [CRITICAL] ...
### [HIGH] ...
### [MEDIUM] ...
### [LOW] ...
## Needs Review
## Clean Areas
```

---

## Escalation Rules

Immediately stop and notify the security channel if any of the following are found:

- A secret or credential in the diff
- A query that allows cross-tenant data access
- An unauthenticated path to scan results or tenant data
- A scan engine that can target internal VirtualRift services
- A `CRITICAL` CVE in a dependency currently used in production

Do not proceed with the audit report — escalate first.

---

## Exit Criteria

The audit is complete when:

- [ ] All entry points have been reviewed
- [ ] All checklist items for the identified area are checked
- [ ] All new dependencies have been scanned for CVEs
- [ ] The attacker simulation has been completed for each entry point
- [ ] The audit report has been produced with all findings documented
- [ ] `CRITICAL` and `HIGH` findings have been escalated or confirmed as resolved

Only mark the audit as `CLEAN` if all items above are complete and no unresolved `CRITICAL` or `HIGH` findings remain.
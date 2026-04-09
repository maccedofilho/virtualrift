# Security Audit Skill

## Trigger

Invoked by `/project:security-audit`.

Use this skill when reviewing any change that may affect security posture, especially auth, tenant isolation, scanners, dependencies, infra or governance files in `.claude`.

---

## Required inputs

Collect:

1. the diff scope or changed files
2. the audit area: `Application`, `Frontend`, `Infrastructure`, `Configuration` or `Governance`
3. whether the change touches auth, tenant logic, scanners, secrets, dependencies or deployment

If the diff is unknown, gather it before auditing.

---

## Execution steps

### Step 1 - Map the attack surface

List all changed entry points and trust boundaries, such as:

- REST or RPC endpoints
- Kafka consumers or producers
- external HTTP calls
- command execution paths
- file uploads, archive handling or filesystem writes
- token, session or permission logic
- dependency or image version changes
- deployment, secret or network policy changes
- `.claude` rules, commands, skills or agents that alter review behavior

### Step 2 - Check the relevant risk classes

Always consider:

- authentication and session lifecycle
- authorization, BOLA/IDOR and tenant isolation
- injection sinks and unsafe input handling
- XSS, CSRF, SSRF, path traversal and unsafe uploads
- secret leakage and weak crypto
- rate limiting, quotas and abuse resistance
- dependency and supply-chain risk
- container, cloud and CI/CD hardening
- scanner abuse against internal VirtualRift infrastructure

### Step 3 - Verify tests and validation

- confirm that critical security behavior is covered by automated tests
- flag empty suites, placeholder assertions or fake `test` scripts as findings
- when a security-sensitive change lacks tests, record that as at least `HIGH` unless clearly mitigated elsewhere

### Step 4 - Simulate attacker paths

For each changed boundary, ask:

- can an unauthenticated actor reach this?
- can one tenant access another tenant's data or workload?
- can user-controlled input reach shell, SQL, file, template or outbound target selection unsafely?
- can this change increase data leakage through logs, reports or UI?
- can a scanner be redirected toward internal services or metadata endpoints?

Document every "yes" as a finding.

### Step 5 - Review dependencies and operational drift

- inspect new or changed dependencies, image tags and manifest values
- flag missing lockfiles, mutable tags or inconsistent versioning when they undermine reproducibility or security
- note when documentation claims controls that the repository does not actually implement

### Step 6 - Produce the report

Use the format from `agents/security-auditor.md`.

---

## Immediate escalation triggers

Stop and escalate immediately for:

- committed secrets or live credentials
- cross-tenant data access
- auth bypass or public access to protected data
- scanner paths that can hit internal infrastructure
- raw user input in command execution paths
- known `CRITICAL` vulnerabilities in active dependencies or images

---

## Exit criteria

The audit is complete only when:

- [ ] all changed trust boundaries were reviewed
- [ ] relevant risk classes were assessed
- [ ] required tests were reviewed or requested
- [ ] critical findings were escalated or resolved
- [ ] the final report clearly distinguishes findings, needs-review items and clean areas
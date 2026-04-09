# Security Audit Command

Invoke with `/project:security-audit`.

Use this command to run a structured security review of code, configuration, infrastructure or governance changes.

## Inputs

Collect:

1. changed files or diff scope
2. audit area: `Application`, `Frontend`, `Infrastructure`, `Configuration` or `Governance`
3. whether auth, tenant isolation, scanners, secrets or dependencies are involved

## Workflow

1. follow `skills/security-audit/SKILL.md`
2. review against `.claude/rules/security.md`, `.claude/rules/testing.md` and `.claude/rules/api-conventions.md`
3. identify blockers, open questions and required follow-up tests
4. output findings in the format required by `agents/security-auditor.md`

## Rules

- Do not mark a change `CLEAN` while unresolved `CRITICAL` or `HIGH` findings remain.
- Missing security tests in critical paths are findings.
- If committed secrets, cross-tenant access or scanner escape paths are found, escalate immediately.

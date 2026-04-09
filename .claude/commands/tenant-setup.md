# Tenant Setup Command

Invoke with `/project:tenant-setup`.

Use this command to guide secure onboarding of a tenant into the current VirtualRift codebase and operational model.

## Inputs

Collect:

1. tenant name and slug
2. plan tier
3. admin email
4. allowed scan targets
5. target environment

## Workflow

1. follow `skills/tenant-setup/SKILL.md`
2. validate that requested artifacts map to paths and modules that actually exist in the repository
3. define tenant isolation, target authorization and quota rules
4. generate or plan only the setup steps that the repository can genuinely support
5. finish with validation, rollback notes and a first secure smoke test plan

## Rules

- Never treat internal-network targets as implicitly allowed.
- Never assume infra manifests or automation exist if the repository does not contain them.
- Tenant setup is incomplete until isolation, quotas and first-scan validation are defined.

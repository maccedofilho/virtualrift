# Tenant Setup Skill

## Trigger

Invoked by `/project:tenant-setup`.

Use it to guide secure onboarding of a tenant in the current VirtualRift codebase and operating model.

---

## Required inputs

Collect before doing anything:

1. tenant name
2. tenant slug
3. plan tier
4. admin email
5. allowed scan targets
6. environment

Do not proceed without all six.

---

## Operating rule

Do not assume infra automation, manifests or migration paths exist unless they are present in the repository. When the repo lacks a required artifact, output a concrete follow-up plan instead of inventing files or commands.

---

## Workflow

### Step 1 - Validate tenant identity and scope

Confirm that:

- the slug is normalized and unique
- the plan maps to real product capabilities
- the first admin account has the correct role model
- allowed targets are explicit and reviewable

### Step 2 - Define isolation requirements

For the tenant, specify:

- tenant identifier propagation rules
- data isolation expectations
- quota boundaries
- scan target authorization model
- report and findings access boundaries

If isolation cannot be shown in code or config, do not mark setup complete.

### Step 3 - Validate target safety

Allowed scan targets must be checked against:

- RFC1918/private ranges unless explicitly supported
- localhost and loopback
- metadata endpoints
- internal service DNS names
- any platform-specific internal assets

### Step 4 - Provision only what the repo supports

When the necessary files exist, update or generate the relevant tenant setup artifacts in:

- tenant service logic
- quota configuration
- target allowlist storage
- secret references
- deployment or namespace config

When the repository does not contain a real implementation path, document the gap and required owner.

### Step 5 - Plan validation and rollback

Define:

- how to confirm the tenant exists with the right quotas and auth boundaries
- how to run a first safe smoke scan
- how to roll back tenant creation or activation if something fails

---

## Exit criteria

Tenant setup is complete only when:

- [ ] tenant identity and quotas are defined
- [ ] allowed targets are explicit and validated
- [ ] isolation boundaries are documented and testable
- [ ] required secrets and config references are identified
- [ ] validation and rollback steps are written down
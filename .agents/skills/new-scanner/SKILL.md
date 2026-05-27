# New Scanner Skill

## Trigger

Invoked by `/project:add-scanner`.

Use this skill to create or extend scanner capability inside VirtualRift.

---

## Required inputs

Collect before writing code:

1. scanner name
2. target type
3. vulnerability category
4. detection strategy
5. expected scan duration
6. known false-positive scenarios

Do not proceed without all six.

---

## Workflow

### Step 1 - Validate the scope

Confirm that:

- the scanner does not duplicate an existing module or rule set
- the target type fits the current product model
- the detection strategy is appropriate
- the scanner can be tested on at least one vulnerable and one clean target

If not, stop and redesign before generating files.

### Step 2 - Map the minimum module

Follow the current repository naming pattern:

```text
backend/virtualrift-{name}-scanner/
src/main/java/com/virtualrift/{name}scanner/
src/test/java/com/virtualrift/{name}scanner/
```

Create only the packages the design really needs, typically `engine`, `service`, `config`, `model` and optionally `rules`.

### Step 3 - Design target safety first

Before implementing detection logic, define:

- how targets are authorized
- how internal networks and metadata endpoints are blocked
- how execution timeouts and concurrency limits are enforced
- how dangerous external tools or payloads are constrained

### Step 4 - Implement the engine and result model

Keep findings actionable and normalized. Every finding should have:

- stable category and severity
- precise but safe location
- enough evidence to prove the issue
- remediation guidance

### Step 5 - Integrate with orchestration

If the design participates in orchestrated scan flow, define:

- request intake path
- state transitions
- success and failure publication
- tenant context propagation

Do not invent orchestrator wiring that the repository cannot support; document the gap instead.

### Step 6 - Add tests immediately

Minimum required tests:

- detector or rule tests
- clean-target regression tests
- internal-target rejection tests
- timeout or failure-path tests where execution is bounded

Use integration tests only where they materially reduce risk.

### Step 7 - Register and document

Update only the artifacts that actually exist in the repository, such as:

- `backend/pom.xml`
- orchestrator enums or routing
- module `application.yml`
- docs for supported scan types

If an infra or deploy artifact does not exist, note it as follow-up work instead of fabricating it.

---

## Exit criteria

The scanner is ready only when:

- [ ] target validation is explicit
- [ ] internal-network and metadata blocking exists
- [ ] execution is bounded
- [ ] tests cover vulnerable and safe paths
- [ ] the module compiles
- [ ] `security-auditor` would have no unresolved `HIGH` or `CRITICAL` concerns
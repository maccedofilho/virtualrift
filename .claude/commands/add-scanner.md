# Add Scanner Command

Invoke this command with `/project:add-scanner`.

Triggers the `skills/new-scanner/SKILL.md` workflow to create a new scan engine from scratch.

## What happens
1. Collects the six required inputs: name, target type, vulnerability category, detection strategy, expected duration and known false positive scenarios
2. Validates the scope against existing scanner modules
3. Generates the full module structure under `backend/virtualrift-{name}-scanner/`
4. Generates detection rules, Kafka integration, Dockerfile, `application.yml` and test skeletons
5. Registers the new scanner in the parent `pom.xml` and the orchestrator
6. Runs the validation checklist from the skill before declaring the scanner ready

## Rules
- Follow `agents/scanner-builder.md` throughout the entire execution
- Never skip the isolation step — every scanner must run as non-root with a read-only filesystem
- Never proceed without a known vulnerable target identified for integration testing
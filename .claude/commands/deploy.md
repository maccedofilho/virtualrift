# Deploy Command

Invoke with `/project:deploy`.

Use this command to guide a safe deployment, not to blindly print `helm upgrade`.

## Inputs to collect

1. target environment: `staging` or `production`
2. modules or services being deployed
3. image or artifact version
4. whether database or contract changes are included
5. whether infra or security-sensitive config changed

## Pre-deployment gates

Do not generate deployment steps until all required gates are answered:

- [ ] Backend and frontend tests relevant to the change are green
- [ ] Security-sensitive diffs were reviewed with `security-auditor`
- [ ] Images use immutable, versioned tags and not `latest`
- [ ] Dependency and image scans have no unresolved `CRITICAL` findings
- [ ] Secrets and config references were reviewed for the target environment
- [ ] Rollback plan exists and names the concrete rollback artifact or prior release
- [ ] Smoke validation criteria are defined in advance

## Deployment guidance

When the repo contains the necessary infra assets, generate commands that:

- use explicit versioned image tags
- include atomic rollback flags where supported
- point to the correct environment values files
- avoid inline secrets
- are accompanied by a rollback command

If the required Helm or manifest files do not exist in the repository, say so explicitly and output a deployment plan instead of fake commands.

## Post-deployment validation

Always include:

- pod or workload health checks
- health endpoint checks for changed services
- log or dashboard checks for error spikes
- one user-level smoke path for the changed feature
- one security-relevant smoke path when auth, tenant isolation or scanners changed

## Rules

- Production requires an explicit confirmation step.
- Do not skip staging if production depends on the same artifact path.
- Do not present undocumented infra as if it already exists in the repo.
- If rollback is undefined, deployment is not ready.
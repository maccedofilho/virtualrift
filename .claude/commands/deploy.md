# Deploy Command

Invoke this command with `/project:deploy`.

Guides a safe deployment of VirtualRift services to the target environment.

## What happens
1. Asks for the target environment: `staging` or `production`
2. Asks which services are being deployed: all or a specific list of modules
3. Runs the pre-deployment checklist
4. Generates the deployment commands for Helm and kubectl
5. Provides the post-deployment validation steps

## Pre-deployment Checklist
Before generating any deployment command, verify:
- [ ] All tests are passing on the target branch
- [ ] The Docker image has been built and pushed with a versioned tag — never `latest`
- [ ] The image has been scanned for CVEs via Trivy — no `CRITICAL` findings
- [ ] The `security-auditor` agent has reviewed any infrastructure changes
- [ ] Database migrations have been reviewed and are backward compatible
- [ ] Secrets in Vault are up to date for the target environment
- [ ] Rollback plan is defined before proceeding

## Deployment Commands
Generate Helm upgrade commands in this format:
```bash
helm upgrade --install virtualrift-{service} ./infra/helm/virtualrift \
  --namespace virtualrift \
  --set image.tag={version} \
  --set environment={environment} \
  --values ./infra/helm/values-{environment}.yaml \
  --atomic \
  --timeout 5m
```

## Post-deployment Validation
After deployment, verify:
- [ ] All pods are running: `kubectl get pods -n virtualrift`
- [ ] Health endpoints return 200: `/actuator/health`
- [ ] No error spikes in Grafana dashboards
- [ ] A smoke test scan completes successfully end-to-end

## Rules
- Never deploy directly to production without deploying to staging first
- Never use `latest` image tags in any environment
- Always use `--atomic` flag — rolls back automatically on failure
- Production deployments require explicit confirmation before generating commands
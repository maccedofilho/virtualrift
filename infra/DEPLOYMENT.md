# Deployment and rollback

VirtualRift deploys immutable container tags to GKE through GitHub Actions and Helm.

## Delivery flow

1. A merge to `main` runs the `Container images` workflow.
2. Every component is published with the same `sha-<full-commit-sha>` tag.
3. A successful image workflow automatically starts `Deploy` for `staging`.
4. Production deploys are started manually from `Deploy` with the full published commit SHA.
5. The workflow confirms that all ten immutable image manifests exist in GHCR before connecting to the cluster.
6. Helm waits for every Deployment and automatically restores the previous revision when Kubernetes readiness fails.
7. External API and frontend smoke tests run after the rollout. A smoke failure restores the previous Helm revision; on a failed first release, the new release is uninstalled.

The `Rollback` workflow can restore a specific Helm revision or select the previous successful revision automatically.

## GitHub environments

Create GitHub Environments named `staging` and `production`. Configure these variables in each environment:

| Variable | Required | Purpose |
|---|---:|---|
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | yes | Workload Identity Federation provider used by GitHub OIDC |
| `GCP_SERVICE_ACCOUNT` | yes | Google service account impersonated by the workflow |
| `GCP_PROJECT_ID` | yes | Project that owns the GKE cluster |
| `GKE_CLUSTER_NAME` | yes | Cluster name from the Terraform `foundation.gke.name` output |
| `GKE_LOCATION` | yes | Cluster location from the Terraform `foundation.gke.location` output |
| `API_HEALTH_URL` | yes | Full HTTPS gateway health URL, ending in `/actuator/health` |
| `FRONTEND_HEALTH_URL` | yes | Full HTTPS dashboard health URL, ending in `/healthz` |
| `FRONTEND_URL` | yes | Public dashboard URL shown in the GitHub deployment |
| `KUBERNETES_NAMESPACE` | no | Defaults to `virtualrift-<environment>` |
| `HELM_RELEASE_NAME` | no | Defaults to `virtualrift` |

The Google service account must be allowed to obtain GKE credentials, and its Kubernetes identity must have the minimum RBAC permissions required to manage the release namespace.

Configure required reviewers and prevent self-review on the `production` environment. This keeps production manual even though staging follows successful `main` image builds automatically.

## Cluster prerequisites

The target namespace must contain these Secrets before deployment:

- `virtualrift-jwt`
- `virtualrift-shared-secrets`
- `virtualrift-auth-db`
- `virtualrift-auth-secrets`
- `virtualrift-tenant-db`
- `virtualrift-tenant-secrets`
- `virtualrift-orchestrator-db`
- `virtualrift-reports-db`

The workflow creates a missing namespace, but it deliberately does not create, copy or print secret material. If the GHCR packages are private, also configure `global.imagePullSecrets` in the environment's Helm values or deployment configuration.

## Manual deployment

Open the `Deploy` workflow from `main`, choose `staging` or `production`, and provide the full 40-character commit SHA produced by `Container images`. The workflow validates that SHA as image data while loading deployment scripts and the chart only from the trusted default branch, then deploys the matching immutable images. Manual deploy and rollback jobs refuse to run from any non-`main` workflow revision.

The same operation can be run from an authenticated workstation:

```bash
KUBERNETES_NAMESPACE=virtualrift-staging \
  ./infra/scripts/deploy-release.sh staging 0123456789abcdef0123456789abcdef01234567
```

## Rollback

Open the `Rollback` workflow and choose the environment. Leave `revision` empty to select the previous successful Helm revision, or provide a revision from:

```bash
helm history virtualrift --namespace virtualrift-staging
```

The local equivalent is:

```bash
KUBERNETES_NAMESPACE=virtualrift-staging \
  ./infra/scripts/rollback-release.sh staging 3
```

Rollback waits for all Deployments and reruns both external smoke tests before reporting success.

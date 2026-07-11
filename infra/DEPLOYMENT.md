# Deployment and rollback

VirtualRift deploys immutable container tags to GKE through GitHub Actions and Helm.

## Delivery flow

1. A merge to `main` runs the `Container images` workflow.
2. Every component is published with the same `sha-<full-commit-sha>` tag.
3. A successful image workflow automatically starts `Deploy` for `staging`.
4. Staging runs external health checks and an authenticated beta E2E journey through the public gateway.
5. A successful staging job records a `release:gate` GitHub deployment in `staging-release-gate` for that exact image commit SHA.
6. Production deploys are started manually from `Deploy` and only accept a SHA with an active successful staging deployment.
7. The workflow confirms that all ten immutable image manifests exist in GHCR before connecting to the cluster.
8. Helm waits for every Deployment and automatically restores the previous revision when Kubernetes readiness fails.
9. External smoke and authenticated E2E tests run after every rollout. A gate failure restores the previous Helm revision; on a failed first release, the new release is uninstalled.

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
| `GKE_FLEET_MEMBERSHIP_NAME` | yes | Membership from the Terraform `foundation.gke.fleet_membership_name` output |
| `CLOUD_SQL_INSTANCE_NAME` | yes | Instance from the Terraform `foundation.cloud_sql.instance_name` output |
| `API_BASE_URL` | yes | Public HTTPS gateway base URL without a trailing path |
| `API_HEALTH_URL` | yes | Full HTTPS gateway health URL, ending in `/actuator/health` |
| `FRONTEND_HEALTH_URL` | yes | Full HTTPS dashboard health URL, ending in `/healthz` |
| `FRONTEND_URL` | yes | Public dashboard URL shown in the GitHub deployment |
| `KUBERNETES_NAMESPACE` | no | Defaults to `virtualrift-<environment>` |
| `HELM_RELEASE_NAME` | no | Defaults to `virtualrift` |

Configure these GitHub Environment secrets separately in `staging` and `production`:

| Secret | Required | Purpose |
|---|---:|---|
| `E2E_USERNAME` | yes | Email of the synthetic release-validation user |
| `E2E_PASSWORD` | yes | Password of the synthetic release-validation user |

The synthetic user must be `ACTIVE`, belong to an active tenant and have at least the `READER` role. Do not reuse a human account. The E2E journey logs in, verifies identity, tenant, quota, scan and report read paths, logs out, and confirms that the access token was revoked. It does not create scans, targets or reports, so repeated releases do not accumulate domain data.

The Google service account must have `roles/container.viewer`, `roles/gkehub.viewer` and `roles/gkehub.gatewayEditor`, plus Kubernetes RBAC limited to the release namespace. The readiness workflow also needs `roles/cloudsql.viewer`; the identity approved for restore drills needs `roles/cloudsql.admin` so it can create and remove the temporary PITR clone.

Configure required reviewers and prevent self-review on the `production` environment. This keeps production manual even though staging follows successful `main` image builds automatically. The workflow uses `deployments: write` to record the explicit `staging-release-gate` qualification and verify its latest status before production. This logical environment carries no credentials; it only prevents the operational `staging` deployment status from superseding the qualification record.

## Cluster prerequisites

### Private control-plane migration

Staging and production deploy only through Connect Gateway. Existing clusters must be migrated in two phases to avoid losing administrative access:

1. set `gke_enable_private_endpoint=false` and `gke_master_authorized_networks` to only the operator's `/32` CIDR temporarily, then apply Terraform so the Fleet membership and required APIs are created
2. grant the workflow service account `roles/gkehub.viewer` and `roles/gkehub.gatewayEditor`, then generate and apply Gateway RBAC for that identity
3. verify `gcloud container fleet memberships get-credentials <membership>` followed by a read and a namespaced write operation
4. restore `gke_enable_private_endpoint=true`, clear `gke_master_authorized_networks`, apply Terraform, and run staging deploy plus rollback drills

Do not close the public endpoint until the Gateway identity can manage the release namespace. New environments use the private endpoint by default. The membership name is available at `foundation.gke.fleet_membership_name`.

Staging and production use External Secrets Operator. Before the first deployment:

- install an operator version that serves `external-secrets.io/v1` for `SecretStore` and `ExternalSecret`
- configure the Vault Kubernetes auth role for the release ServiceAccount and the `vault` token audience
- populate the environment paths and keys listed in [`SECRETS_AND_MESSAGING.md`](SECRETS_AND_MESSAGING.md)
- install a Secret reload controller compatible with the `reloader.stakater.com/auto` annotation, or perform controlled rollouts after rotation

The deployment preflight checks the required External Secrets CRDs. Helm creates the namespaced `SecretStore` and all `ExternalSecret` resources; its atomic wait fails and restores the previous release when the generated Secrets cannot be consumed.

When `externalSecrets.enabled=false`, the target namespace must already contain:

- `virtualrift-jwt`
- `virtualrift-shared-secrets`
- `virtualrift-auth-db`
- `virtualrift-auth-secrets`
- `virtualrift-tenant-db`
- `virtualrift-tenant-secrets`
- `virtualrift-orchestrator-db`
- `virtualrift-reports-db`
- `virtualrift-kafka-client`

The workflow creates a missing namespace, but it never creates, copies or prints secret material itself. If the GHCR packages are private, also configure `global.imagePullSecrets` in the environment's Helm values or deployment configuration.

## Manual deployment

Open the `Deploy` workflow from `main`, choose `staging` or `production`, and provide the full 40-character commit SHA produced by `Container images`. The workflow validates that SHA as image data while loading deployment scripts and the chart only from the trusted default branch, then deploys the matching immutable images. A production request is rejected before environment approval or cloud authentication when that exact SHA does not have a current successful staging deployment. Manual deploy and rollback jobs refuse to run from any non-`main` workflow revision.

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

Rollback waits for all Deployments and reruns the external smoke and authenticated E2E gates before reporting success.

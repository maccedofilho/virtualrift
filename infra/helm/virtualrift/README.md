# VirtualRift Chart

This chart groups the Kubernetes resources required to run the complete VirtualRift platform.

## Components

- `gateway`
- `auth`
- `tenant`
- `orchestrator`
- `reports`
- `web-scanner`
- `api-scanner`
- `network-scanner`
- `sast`
- `frontend`

## Design choices

- The chart can reconcile Kubernetes Secrets from Vault through External Secrets Operator without storing sensitive values in Git; local development can still use pre-created Secrets.
- PostgreSQL, Redis and Kafka are modeled as external dependencies so the same chart can run against managed services.
- Environment overlays only tune runtime shape and public URLs; they do not ship secret material.
- The frontend receives API and OAuth configuration when its container starts, allowing the same image digest to be promoted across environments.
- The default `edge` tags follow `main`; controlled deployments should override them with the immutable `sha-<commit>` tags published by CI.
- Backend management endpoints use isolated ports `9080` through `9088`; only application ports are exposed through ingress.
- Staging and production enable default-deny NetworkPolicies with explicit platform, dependency, monitoring and scanner exceptions.
- Prometheus Operator and Grafana resources are optional so the chart can still run without their CRDs.
- Kafka workloads inherit a shared `SASL_SSL` client configuration in staging and production, including hostname verification and a PEM CA supplied by Secret.
- Database-backed services run Flyway in a short-lived init container; the application container receives only the restricted runtime credential and starts with Flyway disabled.

## Observability

Enable the resources after installing Prometheus Operator and a Grafana dashboard sidecar:

```yaml
observability:
  serviceMonitor:
    enabled: true
  prometheusRule:
    enabled: true
  grafanaDashboard:
    enabled: true
```

Operational setup, metric ports, network assumptions and alert behavior are documented in [`infra/OBSERVABILITY.md`](../../OBSERVABILITY.md).

## Expected secrets

At minimum, the cluster should expose Secrets matching the default references in `values.yaml`:

- `virtualrift-jwt`
- `virtualrift-shared-secrets`
- `virtualrift-auth-db`
- `virtualrift-auth-secrets`
- `virtualrift-tenant-db`
- `virtualrift-tenant-secrets`
- `virtualrift-orchestrator-db`
- `virtualrift-reports-db`
- `virtualrift-kafka-client`

Staging and production enable `externalSecrets` and create a namespaced Vault `SecretStore`. The cluster must have External Secrets Operator installed with the `external-secrets.io/v1` APIs. Secret paths, required keys and rotation behavior are documented in [`infra/SECRETS_AND_MESSAGING.md`](../../SECRETS_AND_MESSAGING.md).

## Example usage

```bash
helm upgrade --install virtualrift infra/helm/virtualrift \
  -f infra/helm/virtualrift/values.yaml \
  -f infra/helm/virtualrift/values-staging.yaml
```

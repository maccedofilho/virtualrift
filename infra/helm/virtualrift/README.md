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

- The chart references pre-created Kubernetes Secrets instead of storing sensitive values in Git.
- PostgreSQL, Redis and Kafka are modeled as external dependencies so the same chart can run against managed services.
- Environment overlays only tune runtime shape and public URLs; they do not ship secret material.
- The frontend receives API and OAuth configuration when its container starts, allowing the same image digest to be promoted across environments.
- The default `edge` tags follow `main`; controlled deployments should override them with the immutable `sha-<commit>` tags published by CI.

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

## Example usage

```bash
helm upgrade --install virtualrift infra/helm/virtualrift \
  -f infra/helm/virtualrift/values.yaml \
  -f infra/helm/virtualrift/values-staging.yaml
```

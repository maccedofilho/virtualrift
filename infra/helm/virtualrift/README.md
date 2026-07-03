# VirtualRift Chart

This chart groups the Kubernetes resources required to run the VirtualRift backend platform.

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

## Design choices

- The chart references pre-created Kubernetes Secrets instead of storing sensitive values in Git.
- PostgreSQL, Redis and Kafka are modeled as external dependencies so the same chart can run against managed services.
- Environment overlays only tune runtime shape and public URLs; they do not ship secret material.

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

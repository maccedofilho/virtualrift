# Infra Workflows

The executable workflows live in the repository-level `.github/workflows` directory:

- `images.yml` validates and publishes immutable application images.
- `deploy.yml` automatically promotes successful `main` images to staging and supports protected manual production deploys.
- `rollback.yml` restores a selected or previous successful Helm revision.

Environment setup, cluster prerequisites and operational procedures are documented in [`infra/DEPLOYMENT.md`](../../DEPLOYMENT.md).

# Database access and role separation

Each service database has two login roles:

- `virtualrift_<service>` is the runtime role. It can read and change application rows, cannot create schema objects, does not own tables, and cannot bypass row-level security.
- `virtualrift_<service>_migrator` owns the database objects and is used only by the Flyway init container before the application starts.

The application container receives only the runtime password and starts with Flyway disabled. The migration password is injected exclusively into the short-lived init container, so a compromised application process cannot reuse the schema-owner credential.
After migrating, the init container revokes every runtime privilege on the Flyway history table.

## Deployments outside Kubernetes

Run the migration entrypoint before starting each database-backed service, with `DB_URL`, `DB_MIGRATION_USER`, `DB_MIGRATION_PASSWORD`, `DB_RUNTIME_USER`, `DB_MIGRATION_LOCATIONS`, and `DB_MIGRATION_TABLE` configured:

```bash
java -Dloader.main=com.virtualrift.common.migration.FlywayMigrationMain \
  -cp application.jar org.springframework.boot.loader.launch.PropertiesLauncher
```

Then start the application with the restricted `DB_USER`/`DB_PASSWORD` and `SPRING_FLYWAY_ENABLED=false`. Non-local runtimes fail closed if embedded Flyway is enabled or the connected role can create schema objects, create temporary objects, access Flyway history, or bypass row-level security.

## Provision or rotate credentials

Export the administrator connection variables and all eight runtime/migration passwords, then run:

```bash
infra/scripts/provision-postgres-service-roles.sh
infra/scripts/verify-postgres-service-roles.sh
```

Required password variables follow `VIRTUALRIFT_<SERVICE>_RUNTIME_DB_PASSWORD` and `VIRTUALRIFT_<SERVICE>_MIGRATION_DB_PASSWORD`, where service is `AUTH`, `TENANT`, `ORCHESTRATOR`, or `REPORTS`.

Store runtime passwords as `password` and migrator passwords as `migration-password` in each Vault database object described in `infra/SECRETS_AND_MESSAGING.md`. Rotate one service at a time, wait for its rollout, run the verifier, and only then revoke the previous credentials.

## Local development

`infra/dev/ensure-postgres-databases.sh` creates the same role layout in the local container. Passwords default to `virtualrift`; the same environment variable names can override them.

Never grant table ownership, `SUPERUSER`, or `BYPASSRLS` to a runtime role. Do not reuse a migrator password as `DB_PASSWORD`.

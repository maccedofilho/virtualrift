# Managed secrets and Kafka security

VirtualRift keeps secret material outside Git and Terraform state. In staging and production, Helm creates a namespaced Vault `SecretStore` and one `ExternalSecret` per application Secret. External Secrets Operator authenticates with Vault through the release ServiceAccount and short-lived Kubernetes tokens.

## Cluster prerequisites

- External Secrets Operator must serve `external-secrets.io/v1` for `SecretStore` and `ExternalSecret`.
- Vault Kubernetes auth must be mounted at `kubernetes` and the environment role must trust the release namespace, ServiceAccount and `vault` audience.
- The Vault KV v2 engine defaults to the `virtualrift` mount.
- A reload controller should honor `reloader.stakater.com/auto: "true"` so refreshed environment-variable Secrets trigger rolling restarts.

The chart defaults to a one-hour refresh interval. It never renders a Vault token, Kafka password, private key or database password.

## Vault layout

Each Vault object is a map whose property names become keys in the generated Kubernetes Secret. The logical paths below are relative to the `virtualrift` KV mount; replace `<environment>` with `staging` or `production`.

| Vault path | Kubernetes Secret | Required properties |
|---|---|---|
| `<environment>/jwt` | `virtualrift-jwt` | `private-key.pem`, `public-key.pem` |
| `<environment>/shared-secrets` | `virtualrift-shared-secrets` | `tenant-internal-api-key` |
| `<environment>/auth-db` | `virtualrift-auth-db` | `password` |
| `<environment>/auth-secrets` | `virtualrift-auth-secrets` | `oauth-state-secret`; `github-client-id` and `github-client-secret` when GitHub OAuth is enabled |
| `<environment>/tenant-db` | `virtualrift-tenant-db` | `password` |
| `<environment>/tenant-secrets` | `virtualrift-tenant-secrets` | `repository-credentials-key-base64` |
| `<environment>/orchestrator-db` | `virtualrift-orchestrator-db` | `password` |
| `<environment>/reports-db` | `virtualrift-reports-db` | `password` |
| `<environment>/kafka-client` | `virtualrift-kafka-client` | `sasl-jaas-config`, `ca.crt` |

`sasl-jaas-config` contains the complete JAAS login module configuration expected by the selected mechanism. For the default `SCRAM-SHA-512`, use the provider-issued username and password in a `ScramLoginModule` statement. `ca.crt` contains the PEM certificate chain that signs the Kafka brokers.

## Kafka enforcement

Local development keeps `PLAINTEXT` and `localhost:9092`. Every other runtime fails during startup unless all of these conditions hold:

- `spring.kafka.security.protocol` is `SASL_SSL`
- the SASL mechanism and JAAS configuration are present
- the broker CA certificate chain is present
- endpoint identification is `https`, preserving broker hostname verification
- bootstrap servers do not point to loopback

Custom producer and consumer factories build from Spring Boot `KafkaProperties`, so these settings apply consistently to tenant, orchestrator, reports and all scanner workers.

## Rotation

Update the value in Vault rather than editing the generated Kubernetes Secret. External Secrets Operator refreshes the Secret, and the reload controller starts a rolling restart so Kafka clients and application processes consume the new value. For credential rotation, keep the old Kafka credential valid until every affected Deployment has completed its rollout, then revoke it at the provider.

If no reload controller is installed, run a controlled `kubectl rollout restart` for the affected Deployments after the ExternalSecret reports `Ready=True`. Deleting or hand-editing generated Secrets is not a supported rotation path.

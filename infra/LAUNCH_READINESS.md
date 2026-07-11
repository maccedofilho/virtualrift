# Beta launch readiness

This document is the go/no-go contract for exposing VirtualRift to beta users. A release is ready only when every mandatory item has current evidence attached to the launch decision.

## Recovery objectives

| Service | RPO | RTO | Recovery mechanism |
|---|---:|---:|---|
| Cloud SQL | 15 minutes | 2 hours | daily backups, seven days of transaction logs, PITR clone and controlled cutover |
| GKE workloads | no persistent data | 30 minutes | regional cluster, Helm immutable release and same-SHA rollback |
| Redis | best effort | 30 minutes | `STANDARD_HA`; caches and short-lived state are rebuilt by applications |
| Reports bucket | 24 hours | 4 hours | object versioning and provider recovery procedure |
| External Kafka/Vault | provider SLA | 4 hours | provider runbook, replicated service and tested credentials rotation |

These objectives are beta targets, not contractual customer SLAs. Any material change to storage, topology or providers requires reviewing them.

## Automated evidence

Run `Launch readiness` from `main` against staging:

1. `backup` verifies that Cloud SQL is runnable, regional, deletion-protected, PITR-enabled, retains the required backup count and has a successful backup no older than 30 hours. It also confirms that GKE has private nodes, a private-only endpoint and a ready Fleet membership.
2. `capacity` runs 25 concurrent synthetic users for ten steady-state minutes. It requires more than 99% successful checks, less than 1% HTTP/authenticated failures, overall p95 below 750 ms and authenticated p95 below 1 second.
3. `restore-drill` requires the exact `RESTORE` confirmation, creates a temporary clone from five minutes in the past, checks PostgreSQL and the four application databases, then removes the clone.
4. `full` runs all three gates and is restricted to staging. Production backup and restore checks are separate, reviewer-approved runs.

Keep capacity artifacts for 30 days. A production launch requires a successful staging `full` run and a successful production `backup` run for the candidate release window. A production restore drill must have succeeded in the last 30 days.

## Go/no-go checklist

- The candidate SHA passed CI, image security gates, staging deploy, authenticated E2E and staging `full` readiness.
- Production deploy promotes that exact staging-qualified SHA; no image is rebuilt between environments.
- GKE public endpoint is disabled, Fleet membership is active, and deploy plus rollback work through Connect Gateway.
- The latest production Cloud SQL backup is under 30 hours old and a production PITR drill succeeded within 30 days.
- Alert delivery to the on-call channel was tested, and the responder can open every linked runbook.
- DNS, TLS, API/frontend health URLs and the synthetic account were verified from outside the cluster.
- Production GitHub Environment requires reviewers, prevents self-review and contains all variables and secrets in `DEPLOYMENT.md`.
- Kafka and Vault providers have active HA/backup contracts and named incident contacts.
- An incident commander, rollback owner and launch decision owner are named for the release window.
- The launch decision records links to workflow runs, dashboards, restore evidence and accepted residual risks.

Any failed mandatory item is a no-go. Waivers require an owner, explicit expiry and rollback trigger; security, backup/PITR, private control-plane access and rollback are not waivable for beta.

## Launch sequence

1. Freeze unrelated changes and run staging `full` from `main`.
2. Run production `backup`, verify alert routing and review current provider status.
3. Record go/no-go evidence and obtain approval from engineering and operations owners.
4. Promote the exact qualified SHA with `Deploy`, then watch errors, latency, saturation and queue lag for at least 30 minutes.
5. On a release-gate or SLO failure, stop traffic expansion and use `Rollback`. For database loss or corruption, follow `runbooks/database-recovery.md`.

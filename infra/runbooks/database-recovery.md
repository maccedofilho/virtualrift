# Cloud SQL recovery

Use this runbook for unavailable, corrupted or accidentally modified PostgreSQL data. Prefer a new PITR instance over restoring in place so the source remains available for investigation and comparison.

## Triage

1. Declare the incident, freeze deploys and record the suspected corruption start time in UTC.
2. Check Cloud SQL state, application error rate and whether the issue is connectivity, capacity or data integrity.
3. Preserve logs and audit evidence. Do not delete or modify the source instance.
4. Select a restore point at least five minutes before the first confirmed bad write and within the seven-day PITR window.

## Recover

1. Run `Launch readiness` with `restore-drill` against the affected environment and enter `RESTORE`. This validates that a temporary PITR clone can be created and inspected without changing the source.
2. For a real recovery, create a separately named PITR clone at the approved timestamp and validate all four `virtualrift_*` databases with service owners.
3. Put write paths in maintenance mode, capture a final source snapshot if safe, and update the secret/configuration reference to the recovered instance.
4. Roll workloads gradually, run smoke and authenticated E2E checks, then monitor errors and data consistency.
5. Keep the old instance protected until the incident owner approves disposal. Never use the drill script as the production cutover mechanism.

## Close

Record actual RPO/RTO, restore timestamp, validation evidence and any lost transactions. Rotate credentials if exposure is suspected, remove temporary instances, reopen deploys and schedule corrective actions before closing the incident.

# High JVM heap usage

## Trigger

A component has used more than 85% of its maximum JVM heap for fifteen minutes.

## Triage

1. Confirm whether heap returns to normal after garbage collection or grows continuously.
2. Compare heap use with request volume, active scans, Kafka lag and container memory.
3. Inspect logs for `OutOfMemoryError`, long GC pauses or unusually large scan/report payloads.
4. Verify that the container limit and JVM `MaxRAMPercentage` leave room for native memory.
5. Check whether the issue affects every replica or a single workload partition.

## Mitigation

Reduce incoming scan concurrency if heap grows with workload, then capture diagnostics according to the incident data-handling policy. Increase memory only after confirming expected workload growth; roll back when a release introduces retained allocations.

## Escalation

Escalate before heap exhaustion when usage is monotonic, GC pauses affect availability, or scanner evidence may contain sensitive customer data requiring controlled diagnostics.

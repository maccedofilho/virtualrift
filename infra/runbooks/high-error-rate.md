# High HTTP error rate

## Trigger

A component has returned more than 5% HTTP 5xx responses for ten minutes.

## Triage

1. Use the alert `component` label to isolate the service in Grafana and logs.
2. Group JSON logs by exception type, route and dependency failure without exposing tenant payloads.
3. Compare request rate, p95 latency, JVM heap and restart metrics over the same interval.
4. Check PostgreSQL, Redis, Kafka and downstream service health for correlated failures.
5. Compare the start of the error increase with the latest deployment revision.

## Mitigation

Roll back when the error rate follows a release. For dependency incidents, reduce nonessential workload and preserve scan event durability rather than repeatedly retrying at high volume.

## Escalation

Escalate when gateway or auth exceeds the threshold, errors span multiple components, or customer data integrity may be affected.

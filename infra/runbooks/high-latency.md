# High request latency

## Trigger

A component's p95 HTTP latency has exceeded two seconds for ten minutes.

## Triage

1. Confirm whether latency is isolated to one component or visible across the gateway and downstream services.
2. Compare request rate, HPA replica count, CPU, memory and JVM heap over the alert window.
3. Inspect database connection saturation, Redis latency and Kafka producer/consumer health.
4. Look for slow external ownership or OAuth calls in structured logs.
5. Verify whether HPA scale-up is constrained by pending pods or cluster capacity.

## Mitigation

Scale within reviewed limits when the bottleneck is capacity. Roll back when latency follows a release. Avoid increasing timeouts before identifying the saturated dependency, because longer waits can amplify queue pressure.

## Escalation

Escalate when p95 continues rising, scan orchestration queues stop draining, or latency affects authentication and report retrieval simultaneously.

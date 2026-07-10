# Repeated container restarts

## Trigger

A container restarted more than twice in fifteen minutes.

## Triage

1. Run `kubectl describe pod <pod> -n <namespace>` and inspect the last termination reason and exit code.
2. Read previous-container logs with `kubectl logs <pod> -n <namespace> -c <container> --previous`.
3. Check for `OOMKilled`, failed probes, invalid production configuration or read-only filesystem writes.
4. Compare memory use and JVM heap pressure with the Grafana overview dashboard.
5. Determine whether all replicas restart or only pods on one node.

## Mitigation

Roll back configuration or application regressions. For verified memory pressure, adjust both JVM behavior and Kubernetes memory requests/limits through reviewed Helm values; do not apply ad hoc live patches.

## Escalation

Escalate immediately for crash loops affecting gateway, auth or all replicas of any stateful workflow component.

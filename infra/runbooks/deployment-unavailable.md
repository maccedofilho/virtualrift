# Deployment unavailable

## Trigger

A VirtualRift Deployment has reported unavailable replicas for more than ten minutes.

## Triage

1. Run `kubectl rollout status deployment/<deployment> -n <namespace>`.
2. Compare desired, available and updated replicas with `kubectl get deployment <deployment> -n <namespace> -o wide`.
3. Review pending pods for resource pressure, topology constraints, PDB conflicts and image pull failures.
4. Check readiness output on the component management port and inspect dependency health.
5. Review the latest Helm revision with `helm history virtualrift -n <namespace>`.

## Mitigation

Resolve cluster capacity or dependency failures first. Roll back when the unavailable replicas correlate with the latest application release. Do not scale below the configured PDB or HPA minimum as a shortcut.

## Escalation

Escalate when the rollout threatens the environment error budget, when zero replicas are available, or when node capacity cannot satisfy the production requests.

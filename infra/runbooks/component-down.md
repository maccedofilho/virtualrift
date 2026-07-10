# Component down

## Trigger

Prometheus has failed to scrape a VirtualRift component for at least five minutes.

## Triage

1. Identify the affected `component`, namespace and current release revision from the alert labels.
2. Run `kubectl get pods,svc,endpoints -n <namespace> -l app.kubernetes.io/component=<component>`.
3. Inspect `kubectl describe pod` for probe failures, image pull errors, scheduling failures or denied mounts.
4. Inspect recent JSON logs with `kubectl logs -n <namespace> -l app.kubernetes.io/component=<component> --tail=200`.
5. Confirm that the monitoring NetworkPolicy selectors match the Prometheus namespace.

## Mitigation

Restart only a demonstrably stuck Deployment with `kubectl rollout restart deployment/<release>-<component> -n <namespace>`. If the alert began after a release, use the protected rollback workflow instead of editing the live Deployment.

## Escalation

Escalate immediately when gateway or auth is unavailable, multiple components fail together, or the issue indicates a managed dependency or cluster-wide network failure.

# Observability and operational hardening

VirtualRift exposes health and Prometheus metrics on isolated management ports and ships optional Prometheus Operator and Grafana resources in its Helm chart.

## Backend telemetry

Every backend service imports `virtualrift-observability.yml` from `virtualrift-common` and provides:

- JSON console logs with service, environment, level, logger, message, MDC and stack trace fields
- graceful Spring shutdown with a 25-second shutdown phase
- `/actuator/health`, `/actuator/health/liveness` and `/actuator/health/readiness`
- `/actuator/prometheus` with application and environment labels
- HTTP request latency histograms with service-level buckets

Management traffic is not routed through the public ingress. Ports are deterministic:

| Component | Management port |
|---|---:|
| gateway | `9080` |
| auth | `9081` |
| tenant | `9082` |
| orchestrator | `9083` |
| reports | `9084` |
| web-scanner | `9085` |
| api-scanner | `9086` |
| network-scanner | `9087` |
| sast | `9088` |

## Prometheus Operator

`ServiceMonitor`, `PrometheusRule` and the Grafana dashboard ConfigMap are disabled by default because their CRDs and dashboard sidecar are external cluster dependencies. After installing a compatible monitoring stack, enable them with environment-specific values:

```yaml
observability:
  serviceMonitor:
    enabled: true
    labels:
      release: kube-prometheus-stack
  prometheusRule:
    enabled: true
    labels:
      release: kube-prometheus-stack
  grafanaDashboard:
    enabled: true
```

The ServiceMonitor attaches `component` and `release` labels to scraped metrics. The provided alerts cover target availability, unavailable replicas, repeated restarts, HTTP 5xx ratio, p95 latency and JVM heap pressure. Alert annotations link directly to the runbooks in `infra/runbooks`.

## Kubernetes runtime

The chart applies these defaults to every component:

- read-only root filesystem with a size-limited `emptyDir` mounted at `/tmp`
- no automatically mounted ServiceAccount token
- no Kubernetes service-link environment injection
- startup, liveness and readiness probes
- graceful termination with pre-stop delay
- rolling updates with zero unavailable replicas
- HPA scale-down stabilization
- hostname topology spreading

The frontend runtime configuration is generated in `/tmp/runtime-config.js`, allowing it to use the same read-only root filesystem as the backend.

## Network policies

NetworkPolicies are enabled in staging and production. They default-deny ingress and egress, then allow:

- traffic between pods from the same VirtualRift release
- DNS through the configured `kube-system` DNS selector
- PostgreSQL, Redis and Kafka dependency ports
- public ingress to gateway and frontend from the configured ingress controller
- metrics ingress from the configured monitoring namespace
- HTTP/HTTPS egress for auth OAuth and tenant ownership verification
- unrestricted egress for scanner workers, because scanning arbitrary customer-authorized targets is a core product capability

Review `networkPolicy.ingress`, `networkPolicy.monitoring` and `networkPolicy.dns` selectors against the labels used by each cluster before the first protected deployment. Application-level target validation remains the security boundary for scanner destinations.

# VirtualRift

> Find the cracks before someone else does.

VirtualRift is a multi-tenant security platform designed to connect to a customer's repository, website or exposed application surface, inspect how the system is built and operated, and identify vulnerabilities, weak security controls and data exposure risks before they become incidents.

The product goal is to analyze real attack paths across source code, APIs, web applications and infrastructure signals, then return actionable findings with enough context for engineering teams to fix the issue instead of just cataloging noise.

## Product Vision

VirtualRift is being built as a backend-first SaaS product for automated security assessment.

The target experience is:

- connect a repository, site or application target
- detect the security mechanisms the system already uses
- evaluate whether those controls are missing, misconfigured or bypassable
- find vulnerabilities and likely breach paths
- explain impact, affected area and remediation guidance
- support multiple programming languages and application stacks from the same platform

## Planned Analysis Coverage

VirtualRift is intended to inspect four major surfaces:

| Surface | Planned focus |
|---|---|
| Source code | insecure coding patterns, secrets exposure, weak auth flows, dangerous dependencies, insecure defaults |
| Web applications | OWASP-style issues, exposed inputs, weak headers, client-side flaws, session problems |
| APIs | broken authentication, authorization flaws, excessive data exposure, weak rate limiting, insecure contracts |
| Infrastructure and runtime signals | weak TLS, exposed services, insecure edge configuration, unsafe deployment posture |

## Language Strategy

The product is planned to support multiple languages and frameworks.

The initial target is to start with the five most-used frontend ecosystems and the five most-used backend ecosystems, then expand based on demand and detection quality.

At this stage, the repository does not claim full language coverage yet. The backend platform remains the technical core so new analyzers can be added on top of a stable orchestration, tenancy and reporting model, while the frontend beta now exposes the first operational workflows on top of that backend.

## Current Delivery Phase

VirtualRift is currently in a backend-first beta phase.

That means:

- the backend services still define the main delivery pace and product boundaries
- the frontend is no longer just preparatory structure; it already exposes the first real beta workflows
- auth, tenancy, scan orchestration, reporting and scanner workers are already connected by real contracts
- some product surfaces are still foundational and not yet at a polished GA experience

## Repository Status

This monorepo already contains a working backend platform, an operational frontend beta and the supporting project governance needed to keep the security scope coherent.

### What is implemented now

- Java 21 Spring Boot backend organized as a Maven multi-module system
- API Gateway, Auth, Tenant, Orchestrator and Reports services
- scanner workers for web, API, network and SAST analysis over Kafka
- shared backend types and domain primitives in `backend/virtualrift-common`
- tenant-aware scan creation, status, findings, aggregated results and report snapshots
- workspace onboarding, workspace invitations, JWT session refresh/logout and GitHub OAuth login foundation
- frontend dashboard with login in PT-BR, onboarding, targets, scans, reports, account and plan areas
- repository onboarding and authenticated SAST ingestion across HTTPS, scheme-less, SSH-short and provider page URLs
- shared frontend packages for API client and TypeScript contracts
- initial CI with backend test/package and frontend test/lint/build
- production container images for every backend service and the frontend dashboard
- GHCR image validation/publication with immutable commit tags and a mainline `edge` tag
- Helm runtime coverage for the complete platform, including the frontend ingress
- automated staging delivery, protected production deployment and deterministic Helm rollback
- repository rules, commands, agents and skills in `.claude`
- real backend tests with `mvn test`, including Testcontainers-backed E2E coverage when Docker is available
- real frontend `test`, `lint` and `build` scripts

### What is not complete yet

- final frontend product polish and broader beta UX hardening
- deeper repository provider integrations beyond the current normalized clone-based onboarding flow
- full security automation and release governance coverage in `.github/workflows/`
- full production infrastructure promised by the long-term platform vision
- complete scanner depth across all desired languages and frameworks
- final report export/storage pipeline beyond the current JSON and printable HTML foundation

## Architecture

Current repository structure:

```text
virtualrift/
├── backend/          Java 21 + Spring Boot 3 microservices
├── frontend/         React 18 + TypeScript + Vite dashboard beta and shared packages
├── infra/            infrastructure documentation, Helm and Terraform assets
├── libs/             shared Java library area, still mostly placeholder
└── .claude/          project rules, agents, commands and skills
```

Current core backend services:

- `virtualrift-gateway`
- `virtualrift-auth`
- `virtualrift-tenant`
- `virtualrift-orchestrator`
- `virtualrift-reports`

Current scanner modules:

- `virtualrift-web-scanner`
- `virtualrift-api-scanner`
- `virtualrift-network-scanner`
- `virtualrift-sast`

Current frontend application and packages:

- `frontend/apps/virtualrift-dashboard`
- `frontend/packages/virtualrift-api-client`
- `frontend/packages/virtualrift-types`
- `frontend/packages/virtualrift-ui`

## Local Development

### Prerequisites

- Java 21
- Maven 3.9+
- Docker and Docker Compose
- Node.js 20+
- Corepack-enabled `pnpm`

### Backend

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn test
```

`mvn test` covers the real backend suite. The Testcontainers end-to-end flows run when Docker is available.

### Frontend workspace

The frontend already contains the dashboard beta and shared packages used by that application:

```bash
cd frontend
corepack pnpm install
corepack pnpm test
corepack pnpm lint
corepack pnpm build
```

### Container images

The backend uses one parameterized multi-stage Dockerfile. Build a service by passing its Maven module and port:

```bash
docker build backend \
  --build-arg SERVICE_MODULE=virtualrift-gateway \
  --build-arg SERVICE_PORT=8080 \
  --tag virtualrift-gateway:local
```

Build the production dashboard image with:

```bash
docker build frontend --tag virtualrift-frontend:local
```

The dashboard image reads `VITE_VIRTUALRIFT_ENVIRONMENT`, `VITE_API_BASE_URL` and the OAuth start URLs when the container starts, so the same image can be promoted across environments without a frontend rebuild.

## Roadmap Direction

The current sequence is:

1. keep hardening the backend platform and scanner safety boundaries
2. deepen scan orchestration, ownership verification, tenancy, auth and reporting flows
3. expand analyzer coverage across the first target language groups
4. evolve the frontend beta from operational console to a stronger product experience
5. grow into a broader multi-surface security platform

## Claude Code

This repository is configured for Claude Code workflows through `.claude/`.

Available project commands include:

| Command | Description |
|---|---|
| `/project:add-scanner` | Scaffold a new scan engine |
| `/project:scan-review` | Review a completed scan and produce remediation guidance |
| `/project:vuln-report` | Generate a formal vulnerability report |
| `/project:deploy` | Guide a deployment workflow |

## Security Positioning

VirtualRift is intended to be security-critical software, so the repository is being shaped with security review as part of normal development rather than as a final hardening step.

Current backend direction already assumes:

- tenant-aware isolation boundaries
- JWT-based authentication across service boundaries
- RBAC across `OWNER`, `ANALYST` and `READER` flows
- rate limiting and denylist controls at the gateway edge
- masked evidence before persistence/exposure
- review rules for auth, authz, data exposure, injection and scanner abuse

## License

MIT. See [LICENSE](LICENSE).

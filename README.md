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

At this stage, the repository does not claim full language coverage yet. The backend platform is being prepared first so new analyzers can be added on top of a stable orchestration and tenancy model.

## Current Delivery Phase

VirtualRift is currently in backend construction.

That means:

- the backend services are the active implementation focus right now
- the frontend is intentionally not the current delivery focus
- UI, dashboards and polished beta workflows will be shaped after the backend reaches a stronger beta-ready state

The frontend workspace exists, but it should be treated as preparatory structure rather than the finished product experience.

## Repository Status

This monorepo already contains the backend foundation of the platform and the supporting project governance needed to keep the security scope coherent.

### What is implemented now

- Java 21 Spring Boot backend organized as a Maven multi-module system
- API Gateway, Auth, Tenant and Orchestrator services
- scanner modules for web, API, network and SAST analysis at different maturity levels
- shared backend types and domain primitives in `backend/virtualrift-common`
- repository rules, commands, agents and skills in `.claude`
- real backend tests with `mvn test`
- real frontend `test` and `lint` scripts for workspace hygiene, without claiming frontend feature completeness

### What is not complete yet

- full repository ingestion and end-to-end scan onboarding flow
- mature frontend product experience
- complete CI workflows in `.github/workflows/`
- full production infrastructure promised by the long-term platform vision
- complete scanner depth across all desired languages and frameworks

## Architecture

Current repository structure:

```text
virtualrift/
├── backend/          Java 21 + Spring Boot 3 microservices
├── frontend/         workspace kept ready for the future beta UI
├── infra/            infrastructure documentation and future deployment assets
├── libs/             shared libraries area, still mostly placeholder
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

### Frontend workspace checks

The frontend is not the active product surface yet, but the workspace tooling is already validated:

```bash
cd frontend
corepack pnpm install
corepack pnpm test
corepack pnpm lint
```

## Roadmap Direction

The current sequence is:

1. finish stabilizing the backend platform
2. improve scan orchestration, tenancy, auth and reporting flows
3. grow analyzer coverage across the first target language groups
4. move into a beta phase with a real frontend experience
5. evolve the platform into a broader multi-surface security product

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
- rate limiting and denylist controls at the gateway edge
- review rules for auth, authz, data exposure, injection and scanner abuse

## License

MIT. See [LICENSE](LICENSE).

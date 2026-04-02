# VirtualRift

> Find the cracks before someone else does.

VirtualRift is a multi-tenant SaaS platform for automated vulnerability scanning across web applications, REST/GraphQL APIs, network infrastructure and source code — built with Java 21, Spring Boot 3 and React 18.

---

## What it does

VirtualRift gives security teams and developers a single platform to continuously scan their attack surface and get actionable remediation guidance — without requiring deep security expertise to operate.

| Scanner | What it detects |
|---|---|
| **Web Scanner** | OWASP Top 10, XSS, SQLi, CSRF, open redirects, security headers |
| **API Scanner** | Broken auth, excessive data exposure, improper rate limiting, spec drift |
| **Network Scanner** | Open ports, outdated services, weak TLS, CVE matches via NVD |
| **SAST Engine** | Hardcoded secrets, injection flaws, insecure dependencies in source code |

---

## Architecture

Monorepo with a Java microservices backend and a React frontend, deployed on Kubernetes.
```
virtualrift/
├── backend/          Java 21 + Spring Boot 3 — microservices (Maven multi-module)
├── frontend/         React 18 + TypeScript + Vite (pnpm workspaces)
├── infra/            Terraform + Helm + GitHub Actions
├── libs/             Shared Java libraries
└── .claude/          Claude Code configuration — rules, agents, skills and commands
```

**Core services:** API Gateway · Auth · Tenant · Scan Orchestrator · Report Service

**Scan engines:** Web Scanner · API Scanner · Network Scanner · SAST Engine

**Data layer:** PostgreSQL (RLS per tenant) · Elasticsearch · Redis · S3/MinIO

**Observability:** Prometheus · Grafana · Loki · OpenTelemetry · Jaeger

---

## Getting started

### Prerequisites

- Java 21
- Docker + Docker Compose
- Node.js 20+ and pnpm
- Maven 3.9+

### Run locally
```bash
# Clone the repository
git clone https://github.com/your-org/virtualrift.git
cd virtualrift

# Start all infrastructure dependencies
docker-compose up -d

# Build all backend modules
cd backend && mvn clean install

# Start the frontend
cd ../frontend && pnpm install && pnpm dev
```

The dashboard will be available at `http://localhost:5173`.
The API gateway will be available at `http://localhost:8080`.

---

## Claude Code Commands

This repository is configured for use with Claude Code. The following slash commands are available:

| Command | Description |
|---|---|
| `/project:add-scanner` | Scaffold a new scan engine end-to-end |
| `/project:scan-review` | Review a completed scan and generate a remediation plan |
| `/project:vuln-report` | Generate a formal vulnerability report from scan results |
| `/project:deploy` | Guide a safe deployment to staging or production |

---

## Development

### Running tests
```bash
# Backend — all modules
cd backend && mvn test

# Backend — single module
cd backend/virtualrift-auth && mvn test

# Frontend
cd frontend && pnpm test
```

### Code style

Java is enforced via Checkstyle. TypeScript is enforced via ESLint and Prettier.
All conventions are documented in `.claude/rules/code-style.md`.

### Commits

This project uses [Conventional Commits](https://www.conventionalcommits.org).
```
feat(web-scanner): add XSS detection via DOM-based analysis
fix(auth): prevent token reuse after logout
security(gateway): enforce rate limiting per tenant on scan endpoints
```

---

## Security

Security is a first-class concern in this project.

- All services enforce JWT authentication and tenant isolation via PostgreSQL RLS
- Scan engines run in isolated containers with no access to internal infrastructure
- Secrets are managed via HashiCorp Vault — never in source code or environment files
- Every pull request is reviewed against the checklist in `.claude/rules/security.md`

To report a vulnerability, please open a private security advisory on GitHub.
Full security policy is documented in `SECURITY.md`.

---

## Stack

| Layer | Technology |
|---|---|
| Language (backend) | Java 21 |
| Framework | Spring Boot 3, Spring Security, Spring Cloud Gateway, Spring Batch |
| Language (frontend) | TypeScript 5 |
| UI framework | React 18, Vite, TailwindCSS, shadcn/ui |
| Messaging | Apache Kafka |
| Databases | PostgreSQL 16, Elasticsearch 8, Redis 7 |
| Storage | S3 / MinIO |
| Container runtime | Docker, Kubernetes (EKS / GKE) |
| Infrastructure as code | Terraform, Helm |
| CI/CD | GitHub Actions |
| Observability | Prometheus, Grafana, Loki, OpenTelemetry, Jaeger |
| Secret management | HashiCorp Vault |

---

## License

MIT — see [LICENSE](LICENSE) for details.

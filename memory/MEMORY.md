# VirtualRift - Contexto do Projeto

## Visão Geral
Plataforma SaaS multi-tenant para scan automatizado de vulnerabilidades.

## Stack
- **Backend**: Java 21 + Spring Boot 3 (Maven multi-módulo)
- **Frontend**: React 18 + TypeScript + Vite (pnpm workspaces)
- **Infra**: Kubernetes + Terraform + Helm + GitHub Actions

## Módulos Backend (9 módulos)
1. `virtualrift-gateway` - API Gateway (Spring Cloud Gateway)
2. `virtualrift-auth` - Serviço de Autenticação
3. `virtualrift-tenant` - Multi-tenancy
4. `virtualrift-orchestrator` - Orquestração de scans
5. `virtualrift-web-scanner` - Scanner web (OWASP Top 10)
6. `virtualrift-api-scanner` - Scanner de APIs
7. `virtualrift-network-scanner` - Scanner de rede
8. `virtualrift-sast` - SAST (código fonte)
9. `virtualrift-reports` - Geração de relatórios

## Frontend (monorepo pnpm)
- **Apps**: `virtualrift-dashboard`
- **Packages**: `virtualrift-ui`, `virtualrift-api-client`, `virtualrift-types`

## Bibliotecas Compartilhadas (libs/)
- `virtualrift-common` - DTOs, eventos, exceções
- `virtualrift-classifier` - Motor de classificação
- `virtualrift-sdk` - JWT, crypto, sanitizer, network

## Infraestrutura
- Terraform modules: GKE, VPC, Cloud SQL, Redis, GCS, Kafka, Vault
- Helm charts para todos os serviços
- GitHub Actions: CI backend/frontend, deploy staging/production, security-scan

## Regras Importantes
- API: `/api/v1/`, kebab-case, RFC 7807 para erros
- Security: JWT RS256, RLS PostgreSQL, containers isolados
- Code Style: Java 4 spaces, TS 2 spaces, Conventional Commits
- Testing: 80% coverage backend, 70% frontend

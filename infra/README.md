# infra

Concentra os artefatos de infraestrutura usados para levar o VirtualRift de ambiente local para ambientes gerenciados.

## O que esta branch entrega

- chart Helm funcional para os servicos backend e o frontend da plataforma
- values base e overlays por ambiente (`dev`, `staging`, `production`)
- fundacao Terraform para GCP com VPC, GKE, Cloud SQL, Redis e GCS
- integracao de Vault com External Secrets Operator e Kafka autenticado com SASL/TLS, sem embutir segredos no repositorio
- imagens multi-stage executadas sem root para todos os componentes
- validacao e publicacao automatica das imagens no GHCR
- configuracao do dashboard no startup para promover a mesma imagem entre ambientes
- deploy automatico em staging, E2E autenticado e promocao manual do mesmo SHA para production
- rollback Helm manual e recuperacao automatica quando readiness ou smoke tests falham
- metricas Prometheus, logs JSON, alertas, dashboard Grafana e runbooks operacionais
- hardening de pods, probes, rollouts, autoscaling e politicas de rede
- gates de CodeQL, dependencias, segredos, IaC e imagens com attestations verificaveis

O contrato de segredos, os caminhos esperados no Vault, os requisitos do operador e o fluxo de rotacao estao em [`SECRETS_AND_MESSAGING.md`](SECRETS_AND_MESSAGING.md).

## Proximas validacoes de producao

- testes E2E de navegador contra staging
- teste de restauracao, capacidade e conectividade privada do control plane

Consulte [`DEPLOYMENT.md`](DEPLOYMENT.md) para configurar os GitHub Environments, o acesso OIDC ao GKE, os pre-requisitos de Secrets e os procedimentos de deploy e rollback.

Consulte [`OBSERVABILITY.md`](OBSERVABILITY.md) para configurar Prometheus/Grafana, revisar as NetworkPolicies e operar os alertas com os runbooks versionados.

Consulte [`SECURITY_AUTOMATION.md`](SECURITY_AUTOMATION.md) para configurar os checks obrigatorios da `main`, revisar excecoes e verificar attestations de imagens.

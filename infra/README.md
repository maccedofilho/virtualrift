# infra

Concentra os artefatos de infraestrutura usados para levar o VirtualRift de ambiente local para ambientes gerenciados.

## O que esta branch entrega

- chart Helm funcional para os servicos backend e o frontend da plataforma
- values base e overlays por ambiente (`dev`, `staging`, `production`)
- fundacao Terraform para GCP com VPC, GKE, Cloud SQL, Redis e GCS
- contracts Terraform para integrar Kafka e Vault externos sem embutir segredos no repositorio
- imagens multi-stage executadas sem root para todos os componentes
- validacao e publicacao automatica das imagens no GHCR
- configuracao do dashboard no startup para promover a mesma imagem entre ambientes
- deploy automatico em staging e deploy manual protegido em production
- rollback Helm manual e recuperacao automatica quando readiness ou smoke tests falham

## O que esta branch ainda nao tenta resolver

- gestao definitiva de segredos
- hardening operacional fino de rede, observabilidade e mensageria

Esses itens ficam para as proximas branches de preparacao para producao.

Consulte [`DEPLOYMENT.md`](DEPLOYMENT.md) para configurar os GitHub Environments, o acesso OIDC ao GKE, os pre-requisitos de Secrets e os procedimentos de deploy e rollback.

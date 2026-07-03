# infra

Concentra os artefatos de infraestrutura usados para levar o VirtualRift de ambiente local para ambientes gerenciados.

## O que esta branch entrega

- chart Helm funcional para os servicos backend da plataforma
- values base e overlays por ambiente (`dev`, `staging`, `production`)
- fundacao Terraform para GCP com VPC, GKE, Cloud SQL, Redis e GCS
- contracts Terraform para integrar Kafka e Vault externos sem embutir segredos no repositorio

## O que esta branch ainda nao tenta resolver

- build e publicacao de imagens
- pipelines de deploy e rollback
- gestao definitiva de segredos
- hardening operacional fino de rede, observabilidade e mensageria

Esses itens ficam para as proximas branches de preparacao para producao.

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

## O que esta branch ainda nao tenta resolver

- pipelines de deploy e rollback
- gestao definitiva de segredos
- hardening operacional fino de rede, observabilidade e mensageria

Esses itens ficam para as proximas branches de preparacao para producao.

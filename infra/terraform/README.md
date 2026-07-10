# terraform

Reserva os artefatos de infraestrutura como codigo para provisionamento e gestao de recursos de producao do VirtualRift.

## Escopo desta fundacao

Esta base assume Google Cloud como provider principal e cobre:

- rede VPC com sub-redes e Private Service Access
- cluster GKE para workloads da plataforma
- Cloud SQL PostgreSQL com databases por servico
- Redis gerenciado para cache, sessoes e rate limit
- bucket GCS para artefatos e exportacoes
- contracts de integracao para Kafka e Vault externos

## Estrutura

- `modules/`: blocos reaproveitaveis
- `environments/dev`: root Terraform para ambiente de desenvolvimento remoto
- `environments/staging`: root Terraform para homologacao
- `environments/production`: root Terraform para producao

## Observacoes

- O repositorio nao grava segredos em Terraform variables versionadas.
- Os modulos de Kafka e Vault padronizam inputs e outputs, mas assumem servicos externos ja existentes.
- Publicacao de imagens, injecao de segredos, deploy, rollback e gates de seguranca estao versionados em `.github/workflows` e `infra/helm`.

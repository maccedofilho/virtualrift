# Dev Environment

Root Terraform para o ambiente remoto de desenvolvimento.

## Objetivo

- validar a topologia base fora do ambiente local
- manter custo e escala menores que `staging`
- gerar saidas que alimentem o chart Helm com hosts internos de banco, Redis e Kafka

## Arquivos

- `versions.tf`: providers e versao do Terraform
- `main.tf`: chamada do modulo `platform-foundation`
- `terraform.tfvars.example`: exemplo de parametrizacao
- `backend.tf.example`: exemplo de remote state em GCS

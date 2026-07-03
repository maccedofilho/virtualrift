# Production Environment

Root Terraform para producao.

## Objetivo

- provisionar a base gerenciada que vai hospedar o VirtualRift
- manter parametros conservadores para alta disponibilidade
- expor saidas consistentes para a camada de deploy Helm

## Arquivos

- `versions.tf`: providers e versao do Terraform
- `main.tf`: chamada do modulo `platform-foundation`
- `terraform.tfvars.example`: exemplo de parametrizacao
- `backend.tf.example`: exemplo de remote state em GCS

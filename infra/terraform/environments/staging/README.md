# Staging Environment

Root Terraform para homologacao.

## Objetivo

- aproximar runtime, rede e capacidade de producao
- validar mudancas de chart e manifests antes do deploy final
- servir de ponte para smoke tests e rollout controlado nas proximas branches

## Arquivos

- `versions.tf`: providers e versao do Terraform
- `main.tf`: chamada do modulo `platform-foundation`
- `terraform.tfvars.example`: exemplo de parametrizacao
- `backend.tf.example`: exemplo de remote state em GCS

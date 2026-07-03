# helm

Empacota a topologia Kubernetes do VirtualRift.

## Estrutura

- `virtualrift/Chart.yaml`: metadados do chart
- `virtualrift/values.yaml`: defaults compartilhados
- `virtualrift/values-*.yaml`: overlays por ambiente
- `virtualrift/templates/`: manifests Helm para Deployments, Services, Ingress, HPA e PDB

## Objetivo desta fundacao

O chart desta branch assume:

- dependencias gerenciadas fora do chart para PostgreSQL, Redis e Kafka
- injecao de segredos por `Secret` ja existente no cluster
- uma release unica por ambiente contendo gateway, auth, tenant, orchestrator, reports e workers

O frontend e a estrategia final de runtime/containerizacao ficam para a branch de runtime.

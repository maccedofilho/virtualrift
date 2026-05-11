# virtualrift-dashboard

Hospeda o dashboard principal usado por clientes para operar scans, ativos e relatorios.

## Ambiente

Use `.env.example` como base para o ambiente local do dashboard.

Variaveis suportadas:

- `VITE_API_BASE_URL`: base HTTP do gateway
- `VITE_GITHUB_OAUTH_START_URL`: URL inicial do login GitHub, com suporte a `{callbackUrl}`
- `VITE_GOOGLE_OAUTH_START_URL`: URL inicial do login Google, com suporte a `{callbackUrl}` quando o backend suportar esse provider

O callback do login social usa `#/auth/callback` no proprio frontend e pode restaurar a rota original do dashboard depois da autenticacao.

# virtualrift-dashboard

Hospeda o dashboard principal usado por clientes para operar scans, ativos e relatorios.

## Ambiente

Use `.env.example` como base para o ambiente local do dashboard.

Variaveis suportadas:

- `VITE_VIRTUALRIFT_ENVIRONMENT`: `local`, `development`, `staging` ou `production`
- `VITE_API_BASE_URL`: base HTTP do gateway
- `VITE_GITHUB_OAUTH_START_URL`: URL inicial do login GitHub, com suporte a `{callbackUrl}`
- `VITE_GOOGLE_OAUTH_START_URL`: URL inicial do login Google, com suporte a `{callbackUrl}` quando o backend suportar esse provider

Quando `VITE_VIRTUALRIFT_ENVIRONMENT` for diferente de `local`, `VITE_API_BASE_URL` passa a ser obrigatoria e precisa apontar para uma URL HTTPS publica do gateway.

O callback do login social usa `#/auth/callback` no proprio frontend e pode restaurar a rota original do dashboard depois da autenticacao.

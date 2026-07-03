import { VirtualRiftApiError } from '@virtualrift/api-client';

const isNetworkFailure = (message: string): boolean => /failed to fetch|networkerror|load failed/i.test(message);

const readApiDetail = (error: VirtualRiftApiError): string | null => {
  if (!error.data || typeof error.data !== 'object') {
    return null;
  }

  const detail = 'detail' in error.data && typeof error.data.detail === 'string' ? error.data.detail : null;
  const title = 'title' in error.data && typeof error.data.title === 'string' ? error.data.title : null;
  return detail ?? title;
};

const translateApiError = (error: VirtualRiftApiError): string => {
  const detail = readApiDetail(error)?.toLowerCase() ?? '';
  const url = error.response.url;

  switch (error.status) {
    case 401:
      if (error.response.url.includes('/api/v1/auth/token') || detail.includes('invalid credential')) {
        return 'E-mail ou senha inválidos.';
      }
      if (error.response.url.includes('/api/v1/auth/refresh')) {
        return 'Sua sessão expirou. Entre novamente.';
      }
      return 'Sua sessão não é válida para esta operação.';
    case 403:
      if (url.includes('/api/v1/tenants/') && (url.includes('/scan-targets/') || url.endsWith('/scan-targets'))) {
        return 'Seu perfil atual não pode alterar alvos do tenant. Use uma conta com papel OWNER para cadastrar, verificar ou remover alvos.';
      }
      if (url.includes('/api/v1/scans') && !url.includes('/status') && !url.includes('/findings') && !url.includes('/result')) {
        return 'Seu perfil atual não pode criar scans. Use uma conta com papel OWNER ou ANALYST para iniciar novas execuções.';
      }
      if (url.includes('/api/v1/reports/scans/')) {
        return 'Seu perfil atual não pode gerar relatórios. Use uma conta com papel OWNER ou ANALYST para concluir esta ação.';
      }
      return 'Você não tem permissão para concluir esta ação.';
    case 404:
      if (url.includes('/api/v1/auth/onboarding/invitations/preview') || url.includes('/api/v1/auth/onboarding/invitations/accept')) {
        return 'Esse convite não foi encontrado ou já não está mais disponível.';
      }
      return 'O recurso solicitado não foi encontrado.';
    case 409:
      if (url.includes('/api/v1/auth/onboarding/workspaces') || url.includes('/api/v1/auth/onboarding/availability')) {
        if (detail.includes('email is already in use')) {
          return 'Esse e-mail já está em uso.';
        }
        if (detail.includes('workspace slug is already in use')) {
          return 'Esse identificador de workspace já está em uso.';
        }
        if (detail.includes('selected plan is not available')) {
          return 'Esse plano não está disponível para autoatendimento neste momento.';
        }
      }
      if (url.includes('/api/v1/auth/onboarding/invitations/preview') || url.includes('/api/v1/auth/onboarding/invitations/accept')) {
        if (detail.includes('already belongs to an existing account')) {
          return 'Esse e-mail já pertence a uma conta existente. Entre com ele ou peça outro convite.';
        }
        if (detail.includes('no longer available') || detail.includes('expired')) {
          return 'Esse convite expirou, foi revogado ou já foi usado.';
        }
      }
      if (url.includes('/api/v1/tenants/') && url.includes('/invitations')) {
        return 'Já existe um convite pendente para esse e-mail neste workspace.';
      }
      return error.message;
    case 429:
      return 'Você atingiu um limite operacional do seu plano ou tentou a ação rápido demais.';
    default:
      if (error.status === 400 && url.includes('/api/v1/tenants/') && (url.includes('/scan-targets/') || url.endsWith('/scan-targets'))) {
        if (detail.includes('credentials are invalid') || detail.includes('do not have access')) {
          return 'As credenciais configuradas para esse repositório não funcionaram ou não têm acesso suficiente.';
        }
        if (detail.includes('repository was not found') || detail.includes('repository target must be a valid remote repository url')) {
          return 'O repositório informado não pôde ser validado. Revise a URL e confirme se ele está acessível pelo clone HTTPS.';
        }
        if (detail.includes('repository host could not be resolved')) {
          return 'O backend não conseguiu resolver o host desse repositório durante a validação.';
        }
      }
      if (error.status === 503 && (url.includes('/api/v1/auth/onboarding/workspaces') || url.includes('/api/v1/auth/onboarding/availability'))) {
        return 'O onboarding self-service não está disponível neste ambiente agora.';
      }
      if (error.status >= 500) {
        return 'O backend não conseguiu concluir a solicitação agora. Tente novamente em instantes.';
      }
      return error.message;
  }
};

export const toErrorMessage = (error: unknown, fallback: string): string => {
  if (error instanceof VirtualRiftApiError) {
    return translateApiError(error);
  }

  if (error instanceof Error && error.message.trim().length > 0) {
    if (isNetworkFailure(error.message)) {
      return 'Não foi possível alcançar o gateway da API. Verifique se o backend está em execução e se o CORS está configurado.';
    }

    return error.message;
  }

  return fallback;
};

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
      return 'Seu acesso expirou. Entre novamente para continuar.';
    case 403:
      if (url.includes('/api/v1/tenants/') && (url.includes('/scan-targets/') || url.endsWith('/scan-targets'))) {
        return 'Somente quem administra a conta pode adicionar, confirmar ou remover sites e sistemas.';
      }
      if (url.includes('/api/v1/scans') && !url.includes('/status') && !url.includes('/findings') && !url.includes('/result')) {
        return 'Seu acesso permite acompanhar resultados, mas não iniciar uma nova verificação.';
      }
      if (url.includes('/api/v1/reports/scans/')) {
        return 'Seu acesso permite consultar, mas não criar um novo resultado para compartilhar.';
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
          return 'Esse endereço de conta já está em uso.';
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
        return 'Já existe um convite pendente para esse e-mail nesta conta.';
      }
      if (url.includes('/api/v1/tenants/') && (url.includes('/scan-targets/') || url.endsWith('/scan-targets'))) {
        if (detail.includes('target already exists')) {
          return 'Esse site ou sistema já foi adicionado.';
        }
        return 'Esse item já existe ou foi alterado. Atualize a página e tente novamente.';
      }
      return error.message;
    case 429:
      if (url.includes('/api/v1/tenants/') && (url.includes('/scan-targets/') || url.endsWith('/scan-targets'))) {
        return 'Você atingiu o limite de sites ou sistemas do seu plano.';
      }
      return 'Você atingiu o limite do seu plano ou tentou novamente rápido demais.';
    default:
      if (error.status === 400 && url.includes('/api/v1/tenants/') && (url.includes('/scan-targets/') || url.endsWith('/scan-targets'))) {
        if (detail.includes('credentials are invalid') || detail.includes('do not have access')) {
          return 'As credenciais configuradas para esse repositório não funcionaram ou não têm acesso suficiente.';
        }
        if (detail.includes('repository was not found') || detail.includes('repository target must be a valid remote repository url')) {
          return 'Não conseguimos acessar o projeto informado. Revise o link e confirme se a credencial está correta.';
        }
        if (detail.includes('repository host could not be resolved')) {
          return 'Não conseguimos localizar o endereço desse projeto. Confira o link e tente novamente.';
        }
      }
      if (error.status === 503 && (url.includes('/api/v1/auth/onboarding/workspaces') || url.includes('/api/v1/auth/onboarding/availability'))) {
        return 'A criação de contas está temporariamente indisponível. Tente novamente em alguns minutos.';
      }
      if (error.status >= 500) {
        return 'Algo deu errado ao concluir esta ação. Tente novamente em alguns minutos.';
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
      return 'Não conseguimos conectar ao serviço agora. Verifique sua internet e tente novamente.';
    }

    return error.message;
  }

  return fallback;
};

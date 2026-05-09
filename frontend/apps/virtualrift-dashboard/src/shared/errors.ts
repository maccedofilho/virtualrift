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
      return 'Você não tem permissão para concluir esta ação.';
    case 404:
      return 'O recurso solicitado não foi encontrado.';
    case 429:
      return 'Você atingiu um limite operacional do seu plano ou tentou a ação rápido demais.';
    default:
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

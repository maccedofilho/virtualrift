export const toErrorMessage = (error: unknown, fallback: string): string => {
  if (error instanceof Error && error.message.trim().length > 0) {
    if (error.message === 'Failed to fetch') {
      return 'Não foi possível alcançar o gateway da API. Verifique se o backend está em execução e se o CORS está configurado.';
    }

    return error.message;
  }

  return fallback;
};

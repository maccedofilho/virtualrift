export const formatDateTime = (value: string | null): string => {
  if (!value) {
    return 'Indisponível';
  }

  return new Date(value).toLocaleString('pt-BR');
};

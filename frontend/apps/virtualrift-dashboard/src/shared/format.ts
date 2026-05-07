export const formatDateTime = (value: string | null): string => {
  if (!value) {
    return 'Not available';
  }

  return new Date(value).toLocaleString('pt-BR');
};

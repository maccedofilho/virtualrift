const ROLE_LABELS: Record<string, string> = {
  OWNER: 'Proprietário',
  ANALYST: 'Analista',
  READER: 'Leitor',
};

export const hasAnyRole = (grantedRoles: readonly string[], allowedRoles: readonly string[]): boolean =>
  grantedRoles.some((role) => allowedRoles.includes(role));

export const canManageTenantTargets = (roles: readonly string[]): boolean => hasAnyRole(roles, ['OWNER']);

export const canCreateScans = (roles: readonly string[]): boolean => hasAnyRole(roles, ['OWNER', 'ANALYST']);

export const canGenerateReports = (roles: readonly string[]): boolean => hasAnyRole(roles, ['OWNER', 'ANALYST']);

export const canRequestPlanChanges = (roles: readonly string[]): boolean => hasAnyRole(roles, ['OWNER']);

export const formatRoleLabel = (role: string): string =>
  ROLE_LABELS[role] ?? role.charAt(0) + role.slice(1).toLowerCase();

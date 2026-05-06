import { type FormEvent, useEffect, useMemo, useState } from 'react';
import {
  SCAN_TYPES,
  TARGET_TYPES,
  isVerifiedScanTarget,
  type AddScanTargetRequest,
  type ScanTargetResponse,
  type ScanType,
  type TargetType,
  type TenantQuotaResponse,
  type TenantResponse,
  type UUID,
} from '@virtualrift/types';
import { useSession } from './session';

type AuthorizationCheckResult = {
  authorized: boolean;
  scanType: ScanType;
  target: string;
} | null;

const formatDateTime = (value: string | null): string => {
  if (!value) {
    return 'Not available';
  }

  return new Date(value).toLocaleString('pt-BR');
};

const canVerifyTarget = (target: ScanTargetResponse): boolean => target.type !== 'IP_RANGE' && !isVerifiedScanTarget(target.verificationStatus);

const toErrorMessage = (error: unknown, fallback: string): string => {
  if (error instanceof Error && error.message.trim().length > 0) {
    if (error.message === 'Failed to fetch') {
      return 'Could not reach the API gateway. Check if the backend is running and CORS is configured.';
    }

    return error.message;
  }

  return fallback;
};

export function TenantTargetsPanel() {
  const { client, session } = useSession();
  const [tenant, setTenant] = useState<TenantResponse | null>(null);
  const [quota, setQuota] = useState<TenantQuotaResponse | null>(null);
  const [targets, setTargets] = useState<ScanTargetResponse[]>([]);
  const [status, setStatus] = useState<'loading' | 'ready' | 'submitting'>('loading');
  const [error, setError] = useState<string | null>(null);
  const [createTarget, setCreateTarget] = useState('');
  const [createType, setCreateType] = useState<TargetType>('URL');
  const [createDescription, setCreateDescription] = useState('');
  const [authorizeTarget, setAuthorizeTarget] = useState('');
  const [authorizeScanType, setAuthorizeScanType] = useState<ScanType>('WEB');
  const [authorizeStatus, setAuthorizeStatus] = useState<'idle' | 'checking'>('idle');
  const [authorizationResult, setAuthorizationResult] = useState<AuthorizationCheckResult>(null);

  const tenantId = session?.tenantId ?? null;

  const loadWorkspace = async (activeTenantId: UUID) => {
    setStatus('loading');
    setError(null);

    try {
      const [nextTenant, nextQuota, nextTargets] = await Promise.all([
        client.tenants.getById(activeTenantId),
        client.tenants.getQuota(activeTenantId),
        client.tenants.listScanTargets(activeTenantId),
      ]);

      setTenant(nextTenant);
      setQuota(nextQuota);
      setTargets(nextTargets);
      setStatus('ready');
    } catch (loadError) {
      setStatus('ready');
      setError(toErrorMessage(loadError, 'Unable to load tenant targets.'));
    }
  };

  useEffect(() => {
    if (!tenantId) {
      return;
    }

    void loadWorkspace(tenantId);
  }, [client, tenantId]);

  const verifiedTargets = useMemo(
    () => targets.filter((target) => isVerifiedScanTarget(target.verificationStatus)).length,
    [targets],
  );

  const handleCreateTarget = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!tenantId) {
      return;
    }

    const payload: AddScanTargetRequest = {
      target: createTarget,
      type: createType,
      description: createDescription.trim().length > 0 ? createDescription.trim() : null,
    };

    setStatus('submitting');
    setError(null);

    try {
      const createdTarget = await client.tenants.addScanTarget(tenantId, payload);
      setTargets((currentTargets) => [createdTarget, ...currentTargets]);
      setCreateTarget('');
      setCreateDescription('');
      setAuthorizationResult(null);
      setStatus('ready');
    } catch (createError) {
      setStatus('ready');
      setError(toErrorMessage(createError, 'Unable to add scan target.'));
    }
  };

  const handleVerifyTarget = async (targetId: UUID) => {
    if (!tenantId) {
      return;
    }

    setStatus('submitting');
    setError(null);

    try {
      const verifiedTarget = await client.tenants.verifyScanTarget(tenantId, targetId);
      setTargets((currentTargets) =>
        currentTargets.map((currentTarget) => (currentTarget.id === targetId ? verifiedTarget : currentTarget)),
      );
      setStatus('ready');
    } catch (verifyError) {
      setStatus('ready');
      setError(toErrorMessage(verifyError, 'Unable to verify target ownership.'));
    }
  };

  const handleRemoveTarget = async (targetId: UUID) => {
    if (!tenantId) {
      return;
    }

    setStatus('submitting');
    setError(null);

    try {
      await client.tenants.removeScanTarget(tenantId, targetId);
      setTargets((currentTargets) => currentTargets.filter((target) => target.id !== targetId));
      setStatus('ready');
    } catch (removeError) {
      setStatus('ready');
      setError(toErrorMessage(removeError, 'Unable to remove target.'));
    }
  };

  const handleAuthorizationCheck = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!tenantId) {
      return;
    }

    setAuthorizeStatus('checking');
    setError(null);

    try {
      const result = await client.tenants.authorizeScanTarget(tenantId, {
        target: authorizeTarget,
        scanType: authorizeScanType,
      });
      setAuthorizationResult({
        authorized: result.authorized,
        scanType: authorizeScanType,
        target: authorizeTarget,
      });
      setAuthorizeStatus('idle');
    } catch (authorizeError) {
      setAuthorizeStatus('idle');
      setAuthorizationResult(null);
      setError(toErrorMessage(authorizeError, 'Unable to check target authorization.'));
    }
  };

  if (!tenantId) {
    return null;
  }

  return (
    <section aria-label="tenant-targets">
      <h2>Tenant targets</h2>
      {tenant ? <p>Tenant: {tenant.name} ({tenant.slug})</p> : null}
      {tenant ? <p>Plan: {tenant.plan}</p> : null}
      {quota ? <p>Quota: {targets.length}/{quota.maxScanTargets} targets registered</p> : null}
      {quota ? <p>Verified targets: {verifiedTargets}</p> : null}
      {status === 'loading' ? <p>Loading tenant workspace...</p> : null}
      {error ? <p role="alert">{error}</p> : null}

      <section aria-label="create-target">
        <h3>Add scan target</h3>
        <form onSubmit={handleCreateTarget}>
          <label htmlFor="target-value">Target</label>
          <input
            id="target-value"
            name="target"
            type="text"
            value={createTarget}
            onChange={(event) => setCreateTarget(event.target.value)}
            placeholder="https://app.example.com or https://github.com/org/repo"
            required
          />
          <label htmlFor="target-type">Type</label>
          <select id="target-type" name="type" value={createType} onChange={(event) => setCreateType(event.target.value as TargetType)}>
            {TARGET_TYPES.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
          <label htmlFor="target-description">Description</label>
          <input
            id="target-description"
            name="description"
            type="text"
            value={createDescription}
            onChange={(event) => setCreateDescription(event.target.value)}
            placeholder="Primary production application"
          />
          <button type="submit" disabled={status === 'submitting'}>
            {status === 'submitting' ? 'Saving...' : 'Add target'}
          </button>
        </form>
      </section>

      <section aria-label="authorization-check">
        <h3>Check scan authorization</h3>
        <form onSubmit={handleAuthorizationCheck}>
          <label htmlFor="authorize-target">Requested target</label>
          <input
            id="authorize-target"
            name="authorize-target"
            type="text"
            value={authorizeTarget}
            onChange={(event) => setAuthorizeTarget(event.target.value)}
            placeholder="https://app.example.com/login"
            required
          />
          <label htmlFor="authorize-scan-type">Scan type</label>
          <select
            id="authorize-scan-type"
            name="authorize-scan-type"
            value={authorizeScanType}
            onChange={(event) => setAuthorizeScanType(event.target.value as ScanType)}
          >
            {SCAN_TYPES.map((scanType) => (
              <option key={scanType} value={scanType}>
                {scanType}
              </option>
            ))}
          </select>
          <button type="submit" disabled={authorizeStatus === 'checking'}>
            {authorizeStatus === 'checking' ? 'Checking...' : 'Check authorization'}
          </button>
        </form>
        {authorizationResult ? (
          <p>
            Authorization for {authorizationResult.scanType} on {authorizationResult.target}: {' '}
            {authorizationResult.authorized ? 'allowed' : 'denied'}
          </p>
        ) : null}
      </section>

      <section aria-label="registered-targets">
        <h3>Registered targets</h3>
        {targets.length === 0 ? <p>No scan targets registered yet.</p> : null}
        <ul>
          {targets.map((target) => (
            <li key={target.id}>
              <h4>{target.target}</h4>
              <p>Type: {target.type}</p>
              <p>Status: {target.verificationStatus}</p>
              <p>Description: {target.description ?? 'No description'}</p>
              <p>Created at: {formatDateTime(target.createdAt)}</p>
              <p>Verified at: {formatDateTime(target.verifiedAt)}</p>
              <p>Last check: {formatDateTime(target.verificationCheckedAt)}</p>
              {target.verificationToken ? <p>Verification token: {target.verificationToken}</p> : null}
              {canVerifyTarget(target) ? (
                <button type="button" onClick={() => void handleVerifyTarget(target.id)} disabled={status === 'submitting'}>
                  Verify ownership
                </button>
              ) : null}
              <button type="button" onClick={() => void handleRemoveTarget(target.id)} disabled={status === 'submitting'}>
                Remove target
              </button>
            </li>
          ))}
        </ul>
      </section>
    </section>
  );
}

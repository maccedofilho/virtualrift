import { type FormEvent, useEffect, useMemo, useState } from 'react';
import {
  isVerifiedScanTarget,
  type CreateScanRequest,
  type ScanResponse,
  type ScanTargetResponse,
  type ScanType,
  type UUID,
} from '@virtualrift/types';
import { useSession } from './session';

const formatDateTime = (value: string | null): string => {
  if (!value) {
    return 'Not available';
  }

  return new Date(value).toLocaleString('pt-BR');
};

const toErrorMessage = (error: unknown, fallback: string): string => {
  if (error instanceof Error && error.message.trim().length > 0) {
    if (error.message === 'Failed to fetch') {
      return 'Could not reach the API gateway. Check if the backend is running and CORS is configured.';
    }

    return error.message;
  }

  return fallback;
};

const scanTypesForTarget = (target: ScanTargetResponse): ScanType[] => {
  switch (target.type) {
    case 'URL':
      return ['WEB', 'API'];
    case 'API_SPEC':
      return ['API'];
    case 'REPOSITORY':
      return ['SAST'];
    case 'IP_RANGE':
      return [];
  }
};

const targetLabel = (target: ScanTargetResponse): string => `${target.target} (${target.type})`;

export function ScanCreationPanel() {
  const { client, session } = useSession();
  const [targets, setTargets] = useState<ScanTargetResponse[]>([]);
  const [selectedTargetId, setSelectedTargetId] = useState<UUID | ''>('');
  const [requestedTarget, setRequestedTarget] = useState('');
  const [scanType, setScanType] = useState<ScanType>('WEB');
  const [depth, setDepth] = useState('1');
  const [timeout, setTimeoutValue] = useState('30');
  const [createdScans, setCreatedScans] = useState<ScanResponse[]>([]);
  const [status, setStatus] = useState<'loading' | 'ready' | 'submitting' | 'refreshing'>('loading');
  const [error, setError] = useState<string | null>(null);

  const tenantId = session?.tenantId ?? null;

  useEffect(() => {
    if (!tenantId) {
      return;
    }

    const loadTargets = async () => {
      setStatus('loading');
      setError(null);

      try {
        const nextTargets = await client.tenants.listScanTargets(tenantId);
        setTargets(nextTargets);
        setStatus('ready');
      } catch (loadError) {
        setStatus('ready');
        setError(toErrorMessage(loadError, 'Unable to load scan targets for scan creation.'));
      }
    };

    void loadTargets();
  }, [client, tenantId]);

  const verifiedTargets = useMemo(
    () => targets.filter((target) => isVerifiedScanTarget(target.verificationStatus) && scanTypesForTarget(target).length > 0),
    [targets],
  );

  const selectedTarget = useMemo(
    () => verifiedTargets.find((target) => target.id === selectedTargetId) ?? null,
    [selectedTargetId, verifiedTargets],
  );

  const availableScanTypes = useMemo(
    () => (selectedTarget ? scanTypesForTarget(selectedTarget) : []),
    [selectedTarget],
  );

  useEffect(() => {
    if (verifiedTargets.length === 0) {
      setSelectedTargetId('');
      setRequestedTarget('');
      return;
    }

    const hasSelectedTarget = verifiedTargets.some((target) => target.id === selectedTargetId);
    if (!hasSelectedTarget) {
      setSelectedTargetId(verifiedTargets[0].id);
    }
  }, [selectedTargetId, verifiedTargets]);

  useEffect(() => {
    if (!selectedTarget) {
      return;
    }

    setRequestedTarget(selectedTarget.target);

    const nextScanTypes = scanTypesForTarget(selectedTarget);
    if (nextScanTypes.length > 0 && !nextScanTypes.includes(scanType)) {
      setScanType(nextScanTypes[0]);
    }
  }, [scanType, selectedTarget]);

  const handleCreateScan = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!requestedTarget.trim()) {
      return;
    }

    const payload: CreateScanRequest = {
      target: requestedTarget.trim(),
      scanType,
      depth: depth.trim().length > 0 ? Number(depth) : null,
      timeout: timeout.trim().length > 0 ? Number(timeout) : null,
    };

    setStatus('submitting');
    setError(null);

    try {
      const createdScan = await client.scans.create(payload);
      setCreatedScans((currentScans) => [createdScan, ...currentScans]);
      setStatus('ready');
    } catch (createError) {
      setStatus('ready');
      setError(toErrorMessage(createError, 'Unable to create scan.'));
    }
  };

  const handleRefreshScan = async (scanId: UUID) => {
    setStatus('refreshing');
    setError(null);

    try {
      const refreshedScan = await client.scans.getStatus(scanId);
      setCreatedScans((currentScans) =>
        currentScans.map((scan) => (scan.id === refreshedScan.id ? refreshedScan : scan)),
      );
      setStatus('ready');
    } catch (refreshError) {
      setStatus('ready');
      setError(toErrorMessage(refreshError, 'Unable to refresh scan status.'));
    }
  };

  return (
    <section aria-label="scan-creation">
      <h2>Create scan</h2>
      <p>This panel currently exposes the HTTP target flows supported by the backend contract in this branch.</p>
      {status === 'loading' ? <p>Loading verified targets...</p> : null}
      {error ? <p role="alert">{error}</p> : null}
      {verifiedTargets.length === 0 ? <p>No verified targets available for scan creation yet.</p> : null}

      {verifiedTargets.length > 0 ? (
        <form onSubmit={handleCreateScan}>
          <label htmlFor="scan-target-select">Verified target</label>
          <select
            id="scan-target-select"
            name="scan-target-select"
            value={selectedTargetId}
            onChange={(event) => setSelectedTargetId(event.target.value)}
          >
            {verifiedTargets.map((target) => (
              <option key={target.id} value={target.id}>
                {targetLabel(target)}
              </option>
            ))}
          </select>

          <label htmlFor="requested-scan-target">Requested scan target</label>
          <input
            id="requested-scan-target"
            name="requested-scan-target"
            type="text"
            value={requestedTarget}
            onChange={(event) => setRequestedTarget(event.target.value)}
            placeholder="https://app.example.com/login"
            required
          />

          <label htmlFor="scan-type-select">Requested scan type</label>
          <select
            id="scan-type-select"
            name="scan-type-select"
            value={scanType}
            onChange={(event) => setScanType(event.target.value as ScanType)}
          >
            {availableScanTypes.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>

          <label htmlFor="scan-depth">Scan depth</label>
          <input
            id="scan-depth"
            name="scan-depth"
            type="number"
            min="1"
            value={depth}
            onChange={(event) => setDepth(event.target.value)}
          />

          <label htmlFor="scan-timeout">Scan timeout (seconds)</label>
          <input
            id="scan-timeout"
            name="scan-timeout"
            type="number"
            min="1"
            value={timeout}
            onChange={(event) => setTimeoutValue(event.target.value)}
          />

          <button type="submit" disabled={status === 'submitting' || status === 'refreshing'}>
            {status === 'submitting' ? 'Creating scan...' : 'Create scan'}
          </button>
        </form>
      ) : null}

      <section aria-label="created-scans">
        <h3>Created scans in this session</h3>
        <p>Recent scans shown here are the ones created from this dashboard session until a list endpoint exists.</p>
        {createdScans.length === 0 ? <p>No scans created from this session yet.</p> : null}
        <ul>
          {createdScans.map((scan) => (
            <li key={scan.id}>
              <h4>{scan.target}</h4>
              <p>Scan ID: {scan.id}</p>
              <p>Type: {scan.scanType}</p>
              <p>Status: {scan.status}</p>
              <p>Created at: {formatDateTime(scan.createdAt)}</p>
              <p>Started at: {formatDateTime(scan.startedAt)}</p>
              <p>Completed at: {formatDateTime(scan.completedAt)}</p>
              <p>Error: {scan.errorMessage ?? 'No error'}</p>
              <button type="button" onClick={() => void handleRefreshScan(scan.id)} disabled={status === 'submitting' || status === 'refreshing'}>
                Refresh status
              </button>
            </li>
          ))}
        </ul>
      </section>
    </section>
  );
}

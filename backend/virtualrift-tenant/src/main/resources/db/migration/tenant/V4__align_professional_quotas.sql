UPDATE tenant_quotas quotas
SET
    max_concurrent_scans = 10,
    report_retention_days = 90
FROM tenants
WHERE tenants.id = quotas.tenant_id
  AND tenants.plan = 'PROFESSIONAL'
  AND quotas.max_scans_per_day = 100
  AND quotas.max_concurrent_scans = 5
  AND quotas.max_scan_targets = 25
  AND quotas.report_retention_days = 30
  AND quotas.sast_enabled = TRUE;

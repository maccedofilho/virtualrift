# Tenant Setup Skill

## Trigger
This skill is invoked via `/project:tenant-setup`.
Use it to guide the creation and configuration of a new tenant in the VirtualRift platform.

---

## When to Use

- When onboarding a new customer to the platform
- When creating a sandbox or trial tenant for testing
- When setting up an internal tenant for VirtualRift's own infrastructure monitoring
- When writing migrations that affect tenant-scoped tables

---

## Inputs

Before executing any step, collect the following:

1. **Tenant name** — human-readable name (e.g. `Acme Corp`)
2. **Tenant slug** — URL-safe identifier (e.g. `acme-corp`) — used in APIs and namespaces
3. **Plan** — `TRIAL`, `STARTER`, `PROFESSIONAL` or `ENTERPRISE`
4. **Admin email** — the first admin user's email address
5. **Scan targets** — initial list of authorized domains or IP ranges the tenant is allowed to scan
6. **Environment** — `development`, `staging` or `production`

Do not proceed to Step 1 until all six inputs are collected.

---

## Plan Quotas Reference

Use these quotas when generating the tenant configuration:

| Quota | TRIAL | STARTER | PROFESSIONAL | ENTERPRISE |
|---|---|---|---|---|
| Max scans per day | 3 | 20 | 100 | unlimited |
| Max concurrent scans | 1 | 3 | 10 | 25 |
| Max scan targets | 1 | 5 | 25 | unlimited |
| Scan types allowed | WEB | WEB, API | WEB, API, NETWORK | ALL |
| Report retention days | 7 | 30 | 90 | 365 |
| SAST enabled | false | false | true | true |

---

## Execution Steps

### Step 1 — Generate the database migration

Create a Flyway migration file under:
```
backend/virtualrift-tenant/src/main/resources/db/migration/
V{timestamp}__add_tenant_{slug}.sql
```

Generate the following SQL:
```sql
-- Insert tenant record
INSERT INTO tenants (
    id,
    name,
    slug,
    plan,
    status,
    created_at
) VALUES (
    gen_random_uuid(),
    '{Tenant name}',
    '{tenant-slug}',
    '{PLAN}',
    'ACTIVE',
    now()
);

-- Store tenant id for subsequent inserts
DO $$
DECLARE
    v_tenant_id UUID;
    v_user_id   UUID;
BEGIN
    SELECT id INTO v_tenant_id FROM tenants WHERE slug = '{tenant-slug}';

    -- Insert admin user
    INSERT INTO users (
        id,
        tenant_id,
        email,
        role,
        status,
        created_at
    ) VALUES (
        gen_random_uuid(),
        v_tenant_id,
        '{admin-email}',
        'ADMIN',
        'PENDING_VERIFICATION',
        now()
    ) RETURNING id INTO v_user_id;

    -- Insert scan quotas
    INSERT INTO tenant_quotas (
        tenant_id,
        max_scans_per_day,
        max_concurrent_scans,
        max_scan_targets,
        report_retention_days,
        sast_enabled
    ) VALUES (
        v_tenant_id,
        {max_scans_per_day},
        {max_concurrent_scans},
        {max_scan_targets},
        {report_retention_days},
        {sast_enabled}
    );

    -- Insert authorized scan targets
    INSERT INTO tenant_scan_targets (tenant_id, target, type, created_at)
    VALUES
        {targets};

END $$;
```

Fill all placeholders with the values collected in the inputs section.

### Step 2 — Enable Row-Level Security

Verify that RLS is active on all tenant-scoped tables. If adding a new tenant-scoped table, generate the following policies:
```sql
-- Enable RLS on the table
ALTER TABLE {table_name} ENABLE ROW LEVEL SECURITY;

-- Restrict SELECT to the current tenant
CREATE POLICY tenant_isolation_select
ON {table_name}
FOR SELECT
USING (tenant_id = current_setting('app.current_tenant_id')::uuid);

-- Restrict INSERT to the current tenant
CREATE POLICY tenant_isolation_insert
ON {table_name}
FOR INSERT
WITH CHECK (tenant_id = current_setting('app.current_tenant_id')::uuid);

-- Restrict UPDATE to the current tenant
CREATE POLICY tenant_isolation_update
ON {table_name}
FOR UPDATE
USING (tenant_id = current_setting('app.current_tenant_id')::uuid);

-- Restrict DELETE to the current tenant
CREATE POLICY tenant_isolation_delete
ON {table_name}
FOR DELETE
USING (tenant_id = current_setting('app.current_tenant_id')::uuid);
```

Confirm RLS is active on these tables before proceeding:
- `scans`
- `scan_results`
- `vulnerability_findings`
- `tenant_scan_targets`
- `tenant_quotas`
- `reports`
- `users`

### Step 3 — Create the Kubernetes namespace

Generate the Kubernetes namespace manifest under:
```
infra/k8s/namespaces/tenant-{slug}.yaml
```
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: tenant-{slug}
  labels:
    virtualrift/tenant: "{slug}"
    virtualrift/plan: "{plan}"
    virtualrift/environment: "{environment}"
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: tenant-isolation
  namespace: tenant-{slug}
spec:
  podSelector: {}
  policyTypes:
    - Ingress
    - Egress
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              virtualrift/role: orchestrator
  egress:
    - to:
        - ipBlock:
            cidr: 0.0.0.0/0
            except:
              - 10.0.0.0/8
              - 172.16.0.0/12
              - 192.168.0.0/16
              - 169.254.0.0/16
              - 127.0.0.0/8
---
apiVersion: v1
kind: ResourceQuota
metadata:
  name: tenant-quota
  namespace: tenant-{slug}
spec:
  hard:
    pods: "{max_concurrent_scans}"
    requests.cpu: "{max_concurrent_scans * 500m}"
    requests.memory: "{max_concurrent_scans * 512Mi}"
    limits.cpu: "{max_concurrent_scans * 1}"
    limits.memory: "{max_concurrent_scans * 1Gi}"
```

### Step 4 — Generate the tenant configuration in Vault

Generate the Vault commands to store tenant-specific secrets:
```bash
# Set the tenant encryption key for findings at rest
vault kv put secret/virtualrift/{environment}/tenants/{slug} \
  findings_encryption_key="$(openssl rand -base64 32)" \
  created_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

# Verify the secret was stored correctly
vault kv get secret/virtualrift/{environment}/tenants/{slug}
```

### Step 5 — Seed Elasticsearch index

Generate the Elasticsearch index for the tenant's findings:
```bash
curl -X PUT "http://localhost:9200/virtualrift-findings-{slug}" \
  -H "Content-Type: application/json" \
  -d '{
    "settings": {
      "number_of_shards": 1,
      "number_of_replicas": 1
    },
    "mappings": {
      "properties": {
        "tenantId":    { "type": "keyword" },
        "scanId":      { "type": "keyword" },
        "severity":    { "type": "keyword" },
        "category":    { "type": "keyword" },
        "location":    { "type": "text" },
        "title":       { "type": "text" },
        "description": { "type": "text" },
        "detectedAt":  { "type": "date" }
      }
    }
  }'
```

### Step 6 — Send the welcome email

Trigger the welcome email to the admin user via the notification service:
```bash
curl -X POST "http://localhost:8080/api/v1/notifications/welcome" \
  -H "Authorization: Bearer {internal_service_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantSlug": "{slug}",
    "adminEmail": "{admin-email}",
    "plan": "{PLAN}"
  }'
```

### Step 7 — Validate the tenant setup

Run through this checklist before declaring the tenant ready:

- [ ] Tenant record exists in the `tenants` table with status `ACTIVE`
- [ ] Admin user exists with role `ADMIN` and status `PENDING_VERIFICATION`
- [ ] Tenant quotas match the plan definition in the reference table
- [ ] All authorized scan targets are inserted and validated against the blocklist
- [ ] RLS policies are active on all tenant-scoped tables
- [ ] Kubernetes namespace exists with network policy and resource quota applied
- [ ] Vault secret is stored and accessible
- [ ] Elasticsearch index exists for the tenant's findings
- [ ] Welcome email was sent successfully to the admin email
- [ ] A test scan can be triggered and reaches `COMPLETED` status end-to-end

---

## Rollback

If any step fails, execute the following rollback in order:
```sql
-- Remove tenant data
DELETE FROM tenant_scan_targets WHERE tenant_id = (SELECT id FROM tenants WHERE slug = '{slug}');
DELETE FROM tenant_quotas       WHERE tenant_id = (SELECT id FROM tenants WHERE slug = '{slug}');
DELETE FROM users               WHERE tenant_id = (SELECT id FROM tenants WHERE slug = '{slug}');
DELETE FROM tenants             WHERE slug = '{slug}';
```
```bash
# Remove Kubernetes namespace
kubectl delete namespace tenant-{slug}

# Remove Vault secret
vault kv delete secret/virtualrift/{environment}/tenants/{slug}

# Remove Elasticsearch index
curl -X DELETE "http://localhost:9200/virtualrift-findings-{slug}"
```

Document the reason for rollback and open an incident report if this happened in production.

---

## Exit Criteria

The tenant setup is complete when:

- [ ] All seven steps are complete
- [ ] The validation checklist in Step 7 is fully checked
- [ ] A test scan has completed successfully end-to-end for the new tenant
- [ ] No data from the new tenant is visible when authenticated as a different tenant
# Scan Review Command

Invoke with `/project:scan-review`.

Review a completed scan result and turn it into an actionable remediation plan without losing accuracy, tenant context or evidence discipline.

## Inputs

Collect:

- scan ID or raw result payload
- scan type: `WEB`, `API`, `NETWORK`, `SAST` or mixed
- audience: `technical` or `executive`
- whether this is a first scan or a re-scan
- any known accepted risks or compensating controls

## Workflow

### 1. Normalize and triage

- group by severity and root cause
- deduplicate repeated manifestations of the same issue
- note confidence if the scanner could produce false positives

### 2. Prioritize by exploitability

Within a severity bucket, prioritize:

1. unauthenticated remote exposure
2. cross-tenant or credential impact
3. public exploit availability
4. blast radius and ease of abuse

### 3. Produce remediation guidance

For each important finding include:

- what it is
- where it was found
- why it matters in real terms
- the safest likely fix
- how to verify the fix
- whether a re-scan alone is enough or a code/config review is also required

### 4. Handle sensitivity correctly

- mask credentials, tokens and excessive source snippets
- keep only the minimum evidence needed to support the conclusion
- call out when a finding could indicate a platform issue rather than only a tenant issue

## Output modes

### Technical

Include:

- scope and target
- counts by severity
- immediate actions
- grouped remediation plan
- verification and re-scan guidance

### Executive

Include:

- overall risk level
- top issues in plain language
- likely business impact
- recommended next steps and owner groups

## Rules

- Never downplay `CRITICAL` or `HIGH` findings.
- Always include a verification step.
- Re-scan reviews must explicitly say what improved, what regressed and what remains unresolved.
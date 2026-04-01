# Scan Review Command

Invoke this command with `/project:scan-review`.

Reviews a completed scan result and produces a prioritized remediation plan for the tenant.

## What happens
1. Asks for the scan ID or the raw scan result JSON
2. Identifies the scan type: WEB, API, NETWORK or SAST
3. Deduplicates and groups findings by category and severity
4. Produces a prioritized remediation plan
5. Generates a summary suitable for a non-technical stakeholder

## Inputs
Before starting, collect:
- **Scan ID or result** — the completed scan output to review
- **Audience** — `technical` (developers) or `executive` (stakeholders)
- **Context** — is this a first scan or a re-scan after remediation?

## Review Steps

### Step 1 — Triage findings
Group all findings by severity: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, `INFO`.
Deduplicate findings that share the same root cause — report them as one finding with multiple affected locations.

### Step 2 — Prioritize by exploitability
Within each severity level, rank findings by:
1. Is it remotely exploitable without authentication?
2. Does it affect tenant data isolation?
3. Does it expose credentials or secrets?
4. Is a public exploit available for this vulnerability?

### Step 3 — Generate remediation plan
For each finding produce:
- **What** — what the vulnerability is and where it was found
- **Why it matters** — real-world impact if exploited
- **How to fix** — specific, actionable remediation steps
- **Effort estimate** — `LOW` (< 1 hour), `MEDIUM` (1 day), `HIGH` (> 1 day)
- **Verification** — how to confirm the fix was applied correctly

### Step 4 — Produce the output

**Technical output format:**
```
## Scan Review — {scan_id}
Target: {target}
Date: {date}
Total findings: {count} (CRITICAL: x, HIGH: x, MEDIUM: x, LOW: x)

## Immediate Action Required (CRITICAL + HIGH)
### 1. {Finding title}
Location: ...
Impact: ...
Fix: ...
Verify: ...

## Should Fix Soon (MEDIUM)
...

## Low Priority (LOW + INFO)
...

## Re-scan Recommendation
Run `/project:scan-review` again after fixes are applied to verify remediation.
```

**Executive output format:**
```
## Security Scan Summary — {date}
Overall risk level: CRITICAL / HIGH / MEDIUM / LOW

{2-3 sentence plain English summary of the risk}

## Top 3 Issues to Fix
1. ...
2. ...
3. ...

## Recommended Next Steps
...
```

## Rules
- Never minimize a `CRITICAL` finding — always recommend immediate action
- Always provide a verification step — remediation without verification is incomplete
- When reviewing a re-scan, explicitly compare against the previous scan and confirm which findings were resolved
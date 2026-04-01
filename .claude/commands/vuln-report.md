# Vulnerability Report Command

Invoke this command with `/project:vuln-report`.

Generates a formal vulnerability report from one or more completed scans, ready to be shared with the tenant or exported as PDF.

## What happens
1. Collects the scan IDs or result data to include in the report
2. Asks for the report type and audience
3. Generates the full structured report
4. Provides the export command to trigger PDF generation via the report service

## Inputs
Before starting, collect:
- **Scan IDs** — one or more completed scan IDs to include
- **Report type** — `FULL` (all findings) or `EXECUTIVE` (summary only)
- **Tenant name** — for the report header
- **Period** — the date range this report covers
- **Audience** — `internal`, `client` or `compliance`

## Report Structure

### Full Report
```
# VirtualRift Security Assessment Report
Tenant: {tenant name}
Period: {start date} – {end date}
Generated: {date}
Report ID: {uuid}

---

## Executive Summary
{3-5 sentence overview of the security posture, key risks and recommended priorities}

## Scan Coverage
| Scan Type | Targets Scanned | Date | Status |
|---|---|---|---|
| Web | ... | ... | COMPLETED |
| API | ... | ... | COMPLETED |

## Risk Overview
| Severity | Count | vs Previous Period |
|---|---|---|
| CRITICAL | x | +2 |
| HIGH | x | -1 |
| MEDIUM | x | 0 |
| LOW | x | +5 |

## Findings

### CRITICAL Findings
#### {Finding title}
- **ID**: VR-{category}-{number}
- **Location**: {url / file / ip}
- **Description**: {what it is}
- **Impact**: {what happens if exploited}
- **Evidence**: {proof of vulnerability}
- **Remediation**: {how to fix}
- **References**: {CVE, OWASP, CWE}

### HIGH Findings
...

### MEDIUM Findings
...

### LOW and INFO Findings
(summarized as a table, not detailed individually)

## Remediation Roadmap
| Priority | Finding | Effort | Owner | Target Date |
|---|---|---|---|---|
| 1 | ... | LOW | Dev team | ... |

## Methodology
Brief description of the scan types performed and tools used.

## Disclaimer
This report reflects the security posture at the time of scanning.
New vulnerabilities may emerge after this report was generated.
```

### Executive Report
Condensed version — executive summary, risk overview, top 5 findings and remediation roadmap only. No raw evidence or technical details.

## Export Command
After generating the report content, trigger PDF export:
```bash
curl -X POST "http://localhost:8080/api/v1/reports/export" \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "scanIds": ["{scan_id_1}", "{scan_id_2}"],
    "reportType": "{FULL|EXECUTIVE}",
    "tenantId": "{tenant_id}",
    "format": "PDF"
  }'
```

## Rules
- Never include raw credentials or secrets found during scanning in the report body — mask them
- Evidence snippets must be limited to what is necessary to prove the finding — no full source code dumps
- Executive reports must be free of technical jargon — write for a non-technical audience
- Always include a disclaimer that the report reflects a point-in-time assessment
- Compliance reports must map each finding to the relevant control: OWASP Top 10, CWE, CVE or ISO 27001
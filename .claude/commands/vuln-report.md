# Vulnerability Report Command

Invoke with `/project:vuln-report`.

Generate a formal report from completed scans that is accurate, audience-appropriate and safe to share.

## Inputs

Collect:

- one or more scan IDs or result payloads
- report type: `FULL` or `EXECUTIVE`
- tenant name
- reporting period
- audience: `internal`, `client` or `compliance`
- whether this report compares against a previous period

## Required sections

### Full report

Include:

- executive summary
- scope and coverage
- risk overview by severity
- detailed findings for `CRITICAL`, `HIGH` and `MEDIUM`
- summarized `LOW` and `INFO` findings
- remediation roadmap
- methodology
- disclaimer

### Executive report

Include only:

- overall risk level
- top priorities
- business-facing impact summary
- remediation roadmap

Do not include raw technical evidence in executive reports.

## Evidence handling rules

- Never include raw credentials, tokens, secrets or excessive source code.
- Mask sensitive target details unless the audience and purpose require them.
- Include enough evidence to support the finding, but no more.
- Map findings to CWE, OWASP, CVE or other relevant standards when possible.

## Export guidance

If the repository and environment expose a real report export endpoint, provide the minimal safe command required to trigger it.
If the endpoint or service is not actually present, say so and stop at the report content.

## Rules

- Reports are point-in-time assessments and must say so explicitly.
- Client-facing reports must avoid unnecessary internal implementation detail.
- Compliance-facing reports should preserve traceability to recognized control frameworks.
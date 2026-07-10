# Security policy

## Reporting a vulnerability

Do not open a public issue for suspected vulnerabilities, leaked credentials or tenant data exposure. Use GitHub private vulnerability reporting from the repository Security tab and include:

- affected component and version or commit
- reproduction steps with sensitive values removed
- expected and observed security impact
- suggested remediation, when available

Use regular GitHub issues only for non-sensitive hardening work. Do not include access tokens, customer targets, raw scan evidence or private repository content in any report.

## Supported version

Until the first stable release, only the latest commit on `main` and the currently deployed production release receive security fixes. Older beta images and feature branches are not supported.

## Response targets

- Critical reports: initial triage within one business day.
- High reports: initial triage within three business days.
- Lower severities: prioritized in the regular maintenance queue.

These targets describe the beta response process and are not a contractual SLA.

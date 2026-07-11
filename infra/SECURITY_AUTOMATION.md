# Security automation and release governance

VirtualRift uses blocking security checks before merge and again when release images are published.

## Pull request gates

Configure the `main` ruleset to require pull requests, at least one approval, dismissal of stale approvals and these status checks:

- `Repository hygiene`
- `Delivery configuration`
- `Backend tests and package`
- `Frontend tests, lint and build`
- `Secret history scan`
- `Dependency review`
- `Dependency and IaC scan`
- `CodeQL (java-kotlin)`
- `CodeQL (javascript-typescript)`
- every backend and frontend job from `Container images`

Prevent force pushes and branch deletion. Restrict bypass permissions to the emergency maintainer group and record every bypass in the incident log.

## What each gate enforces

- Gitleaks scans the complete Git history and rejects new credentials. Historical test fixtures are allowed only by exact fingerprint in `.gitleaksignore`.
- Dependency Review rejects pull requests that introduce runtime dependencies with `HIGH` or `CRITICAL` advisories.
- Trivy rejects fixable `HIGH` or `CRITICAL` dependency and IaC findings. Exceptions belong in `.trivyignore.yaml` with a path, reason and expiration date.
- Every pull request image is loaded locally and scanned for fixable `HIGH` or `CRITICAL` operating-system and application findings before merge.
- CodeQL runs the extended security queries for Java/Kotlin and JavaScript/TypeScript.
- Dependabot opens grouped weekly updates for Maven, pnpm, container bases, Terraform providers and GitHub Actions.
- External GitHub Actions, container build bases, CI tools and Testcontainers images are pinned to immutable SHAs or digests; CI rejects mutable action references.

## Published images

The container workflow already emits an SBOM and BuildKit provenance. For every non-PR build it also:

1. scans the immutable registry digest with Trivy
2. creates a GitHub artifact attestation backed by OIDC/Sigstore
3. verifies the registry attestation with `gh attestation verify`

The staging deploy only starts after the complete `Container images` workflow succeeds, so a failed scan or attestation prevents automatic promotion.

Verify a published image manually with:

```bash
gh attestation verify \
  oci://ghcr.io/maccedofilho/virtualrift-gateway@sha256:<digest> \
  --repo maccedofilho/virtualrift
```

## Exception lifecycle

Security exceptions must identify one finding, stay limited to the narrowest path, explain why exploitation is not currently possible and include an expiration date. Expired exceptions fail the next scheduled scan.

There are no active IaC exceptions. The GKE public control-plane exception was removed after Fleet registration and Connect Gateway became the deployment path.

## Local verification

```bash
docker run --rm -v "$PWD:/repo" -w /repo \
  ghcr.io/gitleaks/gitleaks:v8.28.0@sha256:cdbb7c955abce02001a9f6c9f602fb195b7fadc1e812065883f695d1eeaba854 \
  git --redact --verbose --exit-code 1

docker run --rm -v "$PWD:/repo" -w /repo \
  aquasec/trivy:0.70.0@sha256:be1190afcb28352bfddc4ddeb71470835d16462af68d310f9f4bca710961a41e \
  fs --scanners vuln,misconfig --severity HIGH,CRITICAL --ignore-unfixed \
  --ignorefile .trivyignore.yaml --exit-code 1 --skip-version-check .
```

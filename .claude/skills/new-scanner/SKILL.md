# New Scanner Skill

## Trigger
This skill is invoked via `/project:add-scanner`.
Use it to guide the creation of a new scan engine from scratch inside the VirtualRift platform.

---

## When to Use

- When adding a new vulnerability detection capability to the platform
- When extending an existing scanner with a new detection category
- When integrating a third-party scanning tool as a VirtualRift engine

---

## Inputs

Before writing any code, collect the following information:

1. **Scanner name** — what will this scanner be called? (e.g. `ssl`, `dns`, `secrets`)
2. **Target type** — what does it scan? URL, IP range, Git repository, API spec, Docker image
3. **Vulnerability category** — what class of vulnerabilities does it detect?
4. **Detection strategy** — active probing, passive analysis, pattern matching, banner grabbing or spec analysis
5. **Expected scan duration** — how long should a typical scan take?
6. **Known false positive scenarios** — what legitimate configurations might trigger a false positive?

Do not proceed to Step 1 until all six inputs are collected.

---

## Execution Steps

### Step 1 — Validate the scope

Before creating anything, confirm:

- The scanner does not duplicate an existing engine — check `backend/` for existing scanner modules
- The target type is supported by the orchestrator — valid types are `WEB`, `API`, `NETWORK`, `SAST`, `INFRA`
- The detection strategy is appropriate for the vulnerability category
- The scanner can be tested against a known vulnerable target (DVWA, Juice Shop, Vulhub, Metasploitable)

If any of these checks fail, explain why and suggest an alternative approach before continuing.

### Step 2 — Create the module structure

Create the following folder structure under `backend/`:
```
backend/virtualrift-{name}-scanner/
├── src/
│   ├── main/
│   │   ├── java/com/virtualrift/scanner/{name}/
│   │   │   ├── controller/
│   │   │   │   └── {Name}ScannerController.java
│   │   │   ├── service/
│   │   │   │   └── {Name}ScannerService.java
│   │   │   ├── engine/
│   │   │   │   └── {Name}ScanEngine.java
│   │   │   ├── rules/
│   │   │   │   └── {Name}RuleRegistry.java
│   │   │   ├── model/
│   │   │   │   ├── {Name}ScanRequest.java
│   │   │   │   ├── {Name}ScanResult.java
│   │   │   │   └── {Name}Finding.java
│   │   │   └── config/
│   │   │       └── {Name}ScannerConfig.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/com/virtualrift/scanner/{name}/
│           ├── unit/
│           │   └── {Name}ScanEngineTest.java
│           └── integration/
│               └── {Name}ScannerIntegrationTest.java
├── Dockerfile
└── pom.xml
```

Generate each file with the correct package declaration, class skeleton and Javadoc header.

### Step 3 — Define the detection rules

For each vulnerability the scanner will detect, create a rule following this structure:
```java
public record ScanRule(
    String id,           // VR-{CATEGORY}-{number} e.g. VR-SSL-001
    String title,
    String description,
    Severity severity,
    String category,
    String remediation
) {}
```

Rules must be registered in `{Name}RuleRegistry.java`.

Each rule must have:
- A unique ID following the pattern `VR-{CATEGORY}-{three digit number}`
- At least one known-vulnerable example documented in a comment
- At least one known-safe example documented in a comment
- A remediation string that is actionable and specific

### Step 4 — Implement the Kafka integration

The scanner must consume `scan.requested` and publish `scan.completed` or `scan.failed`.

Generate the following event handlers:
```java
// Consumer — scan.requested
@KafkaListener(topics = "scan.requested", groupId = "virtualrift-{name}-scanner")
public void onScanRequested(ScanRequestedEvent event) {
    // 1. Validate tenantId and target
    // 2. Transition scan status to RUNNING
    // 3. Submit scan job to executor
}

// Producer — scan.completed
public void publishScanCompleted(UUID scanId, UUID tenantId, List<VulnerabilityFinding> findings) {
    // Publish to scan.completed topic
}

// Producer — scan.failed
public void publishScanFailed(UUID scanId, UUID tenantId, String reason) {
    // Publish to scan.failed topic
}
```

### Step 5 — Implement isolation

Generate the `Dockerfile` with the following requirements enforced:
```dockerfile
# Use a specific digest — never latest
FROM eclipse-temurin:21-jre@sha256:<digest>

# Run as non-root
RUN addgroup --system scanner && adduser --system --ingroup scanner scanner
USER scanner

# Read-only filesystem — writable tmp only
VOLUME /tmp

# No hardcoded secrets
ENV SPRING_PROFILES_ACTIVE=production

COPY target/virtualrift-{name}-scanner.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

Generate the `application.yml` with:
```yaml
scanner:
  name: {name}
  timeout-seconds: 300        # maximum scan duration — always enforce
  max-concurrent-scans: 5     # per instance limit
  target-validation:
    blocked-ranges:
      - "127.0.0.0/8"
      - "10.0.0.0/8"
      - "172.16.0.0/12"
      - "192.168.0.0/16"
      - "169.254.0.0/16"
```

### Step 6 — Generate the tests

Generate the following test skeletons:

**Unit test — detection rule**
```java
class {Name}ScanEngineTest {

    @Test
    void detect_{ruleId}_whenVulnerableTarget_returnsHighFinding() {}

    @Test
    void detect_{ruleId}_whenCleanTarget_returnsNoFindings() {}
}
```

**Integration test — full scan lifecycle**
```java
@SpringBootTest
@Testcontainers
class {Name}ScannerIntegrationTest {

    @Test
    void scan_whenKnownVulnerableTarget_detectsExpectedFindings() {
        // Target: DVWA or Juice Shop running in a Testcontainer
    }

    @Test
    void scan_whenCleanTarget_producesZeroFindings() {}

    @Test
    void scan_whenTimeoutExceeded_publishesScanFailedEvent() {}

    @Test
    void scan_whenInternalNetworkTarget_rejectsWithValidationError() {}
}
```

### Step 7 — Register the scanner

After generating the module, make the following updates:

- Add the new module to `backend/pom.xml` as a `<module>` entry
- Add the scanner type to the orchestrator's `ScanType` enum
- Add the Kafka topic bindings to the orchestrator's `application.yml`
- Add a new Helm chart values entry for the scanner deployment
- Update `backend/README.md` with the new scanner listed under available engines

### Step 8 — Validate before finishing

Run through this checklist before declaring the scanner ready:

- [ ] Module structure matches the standard layout
- [ ] All detection rules have unique IDs following `VR-{CATEGORY}-{number}`
- [ ] Kafka consumer validates `tenantId` before processing
- [ ] Internal network blocklist is enforced before any scan execution
- [ ] Scan timeout is enforced — no unbounded execution possible
- [ ] Container runs as non-root with a read-only filesystem
- [ ] Unit tests cover each detection rule for both vulnerable and clean targets
- [ ] Integration test runs against a known vulnerable target in a Testcontainer
- [ ] Module is registered in the parent `pom.xml` and the orchestrator

---

## Exit Criteria

The scanner is ready when:

- [ ] All eight steps are complete
- [ ] The checklist in Step 8 is fully checked
- [ ] The module compiles and all tests pass
- [ ] The security auditor agent has reviewed the engine code and found no `HIGH` or `CRITICAL` issues
- [ ] The scanner has been tested end-to-end via the orchestrator in a local `docker-compose` environment
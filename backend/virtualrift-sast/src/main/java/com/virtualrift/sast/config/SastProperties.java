package com.virtualrift.sast.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "virtualrift.sast")
public class SastProperties {

    private Path workspaceRoot = Path.of(System.getProperty("java.io.tmpdir"), "virtualrift-sast");
    private Set<String> allowedRepositorySchemes = new LinkedHashSet<>(Set.of("https"));
    private long maxRepositoryBytes = 104_857_600L;
    private int maxRepositoryFiles = 20_000;
    private int cloneTimeoutSeconds = 120;
    private boolean localTargetsEnabled = true;

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public Set<String> getAllowedRepositorySchemes() {
        return allowedRepositorySchemes;
    }

    public void setAllowedRepositorySchemes(Set<String> allowedRepositorySchemes) {
        if (allowedRepositorySchemes == null || allowedRepositorySchemes.isEmpty()) {
            this.allowedRepositorySchemes = new LinkedHashSet<>();
            return;
        }
        this.allowedRepositorySchemes = allowedRepositorySchemes.stream()
                .filter(scheme -> scheme != null && !scheme.isBlank())
                .map(scheme -> scheme.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public long getMaxRepositoryBytes() {
        return maxRepositoryBytes;
    }

    public void setMaxRepositoryBytes(long maxRepositoryBytes) {
        this.maxRepositoryBytes = maxRepositoryBytes;
    }

    public int getMaxRepositoryFiles() {
        return maxRepositoryFiles;
    }

    public void setMaxRepositoryFiles(int maxRepositoryFiles) {
        this.maxRepositoryFiles = maxRepositoryFiles;
    }

    public int getCloneTimeoutSeconds() {
        return cloneTimeoutSeconds;
    }

    public void setCloneTimeoutSeconds(int cloneTimeoutSeconds) {
        this.cloneTimeoutSeconds = cloneTimeoutSeconds;
    }

    public boolean isLocalTargetsEnabled() {
        return localTargetsEnabled;
    }

    public void setLocalTargetsEnabled(boolean localTargetsEnabled) {
        this.localTargetsEnabled = localTargetsEnabled;
    }

    public Duration cloneTimeout(Integer requestedTimeout) {
        int configuredTimeout = Math.max(1, cloneTimeoutSeconds);
        if (requestedTimeout == null || requestedTimeout <= 0) {
            return Duration.ofSeconds(configuredTimeout);
        }
        return Duration.ofSeconds(Math.min(configuredTimeout, requestedTimeout));
    }
}

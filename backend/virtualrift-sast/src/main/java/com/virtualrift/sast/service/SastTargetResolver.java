package com.virtualrift.sast.service;

import com.virtualrift.sast.config.SastProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class SastTargetResolver {

    private static final Logger log = LoggerFactory.getLogger(SastTargetResolver.class);

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost",
            "127.0.0.1",
            "0.0.0.0",
            "::1",
            "169.254.169.254",
            "169.254.170.2",
            "metadata.google.internal"
    );

    private final SastProperties properties;
    private final GitClient gitClient;

    public SastTargetResolver(SastProperties properties, GitClient gitClient) {
        this.properties = properties;
        this.gitClient = gitClient;
    }

    public SastTarget resolve(String target, UUID scanId, Integer requestedTimeout) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("SAST target cannot be blank");
        }

        URI uri = parseUri(target.trim());
        if (uri.getScheme() != null) {
            return resolveRepository(uri, scanId, requestedTimeout);
        }

        return resolveLocalPath(target);
    }

    private SastTarget resolveRepository(URI repositoryUri, UUID scanId, Integer requestedTimeout) {
        validateRepositoryUri(repositoryUri);

        Path scanWorkspace = null;
        try {
            Path workspaceRoot = properties.getWorkspaceRoot().toAbsolutePath().normalize();
            Files.createDirectories(workspaceRoot);
            scanWorkspace = Files.createTempDirectory(workspaceRoot, "scan-" + scanId + "-");
            Path repositoryPath = scanWorkspace.resolve("repo");

            gitClient.cloneRepository(repositoryUri, repositoryPath, properties.cloneTimeout(requestedTimeout));
            validateRepositoryLimits(repositoryPath);

            Path cleanupRoot = scanWorkspace;
            return SastTarget.temporary(repositoryPath, () -> deleteQuietly(cleanupRoot));
        } catch (IOException | RuntimeException e) {
            if (scanWorkspace != null) {
                deleteQuietly(scanWorkspace);
            }
            if (e instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("SAST repository ingestion failed", e);
        }
    }

    private SastTarget resolveLocalPath(String target) {
        if (!properties.isLocalTargetsEnabled()) {
            throw new IllegalArgumentException("Local SAST targets are disabled");
        }

        Path path = Path.of(target).toAbsolutePath().normalize();
        validateExistingTarget(path, target);
        validateRepositoryLimits(path);
        return SastTarget.local(path);
    }

    private URI parseUri(String target) {
        try {
            return new URI(target);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid SAST target: " + target, e);
        }
    }

    private void validateRepositoryUri(URI repositoryUri) {
        String scheme = repositoryUri.getScheme().toLowerCase(Locale.ROOT);
        if (!properties.getAllowedRepositorySchemes().contains(scheme)) {
            throw new IllegalArgumentException("Repository scheme is not allowed: " + scheme);
        }
        if (repositoryUri.getHost() == null || repositoryUri.getHost().isBlank()) {
            throw new IllegalArgumentException("Repository host is required");
        }
        if (repositoryUri.getUserInfo() != null) {
            throw new IllegalArgumentException("Repository URL credentials are not allowed");
        }
        if (repositoryUri.getQuery() != null || repositoryUri.getFragment() != null) {
            throw new IllegalArgumentException("Repository URL query and fragment are not allowed");
        }

        validateHost(repositoryUri.getHost());
    }

    private void validateHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (BLOCKED_HOSTS.contains(normalized)
                || normalized.endsWith(".localhost")
                || normalized.endsWith(".local")
                || normalized.endsWith(".internal")) {
            throw new IllegalArgumentException("Repository host is not allowed: " + host);
        }

        if (isPrivateIpv4(normalized)) {
            throw new IllegalArgumentException("Repository host is in a private network range: " + host);
        }
    }

    private boolean isPrivateIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        try {
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);
            if (first == 10 || first == 127 || first == 0) {
                return true;
            }
            if (first == 172 && second >= 16 && second <= 31) {
                return true;
            }
            if (first == 192 && second == 168) {
                return true;
            }
            return first == 169 && second == 254;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void validateExistingTarget(Path path, String originalTarget) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("SAST target does not exist: " + originalTarget);
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("SAST target must be a file or directory: " + originalTarget);
        }
    }

    private void validateRepositoryLimits(Path root) {
        long bytes = 0;
        int files = 0;

        try (Stream<Path> paths = Files.walk(root)) {
            var iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    files++;
                    if (files > properties.getMaxRepositoryFiles()) {
                        throw new IllegalArgumentException("SAST target exceeds file limit");
                    }

                    bytes += Files.size(path);
                    if (bytes > properties.getMaxRepositoryBytes()) {
                        throw new IllegalArgumentException("SAST target exceeds size limit");
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("SAST target could not be inspected", e);
        }
    }

    private void deleteQuietly(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Failed to delete SAST workspace path: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to delete SAST workspace: {}", root, e);
        }
    }
}

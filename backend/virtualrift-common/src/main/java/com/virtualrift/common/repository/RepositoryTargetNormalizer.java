package com.virtualrift.common.repository;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class RepositoryTargetNormalizer {

    private RepositoryTargetNormalizer() {
    }

    public static Optional<URI> toCanonicalRemoteUri(String target) {
        if (target == null || target.isBlank()) {
            return Optional.empty();
        }

        String trimmed = target.trim();
        if (isScpLikeTarget(trimmed)) {
            int at = trimmed.indexOf('@');
            int separator = trimmed.indexOf(':', at + 1);
            String host = trimmed.substring(at + 1, separator);
            String path = trimmed.substring(separator + 1);
            return buildUri("https", null, host, -1, canonicalPath(host, path));
        }

        URI parsed = parseCandidate(trimmed);
        if (parsed == null || parsed.getHost() == null || parsed.getHost().isBlank()) {
            return Optional.empty();
        }

        String scheme = parsed.getScheme() == null ? "https" : parsed.getScheme().toLowerCase(Locale.ROOT);
        String normalizedScheme = scheme.equals("ssh") ? "https" : scheme;
        String userInfo = scheme.equals("ssh") ? null : parsed.getUserInfo();
        return buildUri(
                normalizedScheme,
                userInfo,
                parsed.getHost(),
                parsed.getPort(),
                canonicalPath(parsed.getHost(), parsed.getPath())
        );
    }

    public static String toComparableKey(String target) {
        return toCanonicalRemoteUri(target)
                .map(uri -> authority(uri) + stripGitSuffix(stripTrailingSlash(uri.getPath().toLowerCase(Locale.ROOT))))
                .orElseGet(() -> fallbackComparableKey(target));
    }

    private static URI parseCandidate(String target) {
        try {
            if (target.contains("://")) {
                return URI.create(target);
            }
            if (looksLikeSchemeLessRepository(target)) {
                return URI.create("https://" + target);
            }
            return URI.create(target);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean isScpLikeTarget(String target) {
        int at = target.indexOf('@');
        int separator = target.indexOf(':');
        int slash = target.indexOf('/');
        return at > 0 && separator > at && (slash == -1 || separator < slash);
    }

    private static boolean looksLikeSchemeLessRepository(String target) {
        if (target.startsWith("/") || target.startsWith("./") || target.startsWith("../")) {
            return false;
        }
        int separator = target.indexOf('/');
        if (separator <= 0) {
            return false;
        }

        String firstSegment = target.substring(0, separator);
        return firstSegment.contains(".") || firstSegment.contains(":");
    }

    private static Optional<URI> buildUri(String scheme, String userInfo, String host, int port, String path) {
        try {
            return Optional.of(new URI(
                    scheme.toLowerCase(Locale.ROOT),
                    userInfo,
                    host.toLowerCase(Locale.ROOT),
                    port,
                    stripTrailingSlash(path),
                    null,
                    null
            ));
        } catch (URISyntaxException exception) {
            return Optional.empty();
        }
    }

    private static String canonicalPath(String host, String path) {
        List<String> segments = Arrays.stream((path == null ? "" : path).split("/"))
                .filter(segment -> !segment.isBlank())
                .toList();
        if (segments.isEmpty()) {
            return "/";
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if ((normalizedHost.equals("github.com") || normalizedHost.equals("www.github.com")) && segments.size() >= 2) {
            return "/" + segments.get(0) + "/" + segments.get(1);
        }

        int dashIndex = segments.indexOf("-");
        if (dashIndex > 0) {
            return "/" + String.join("/", segments.subList(0, dashIndex));
        }

        if ((normalizedHost.equals("bitbucket.org") || normalizedHost.equals("www.bitbucket.org"))
                && segments.size() >= 3
                && segments.get(2).equals("src")) {
            return "/" + segments.get(0) + "/" + segments.get(1);
        }

        return "/" + String.join("/", segments);
    }

    private static String authority(URI uri) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (uri.getPort() == -1) {
            return host;
        }
        return host + ":" + uri.getPort();
    }

    private static String fallbackComparableKey(String target) {
        return stripTrailingSlash((target == null ? "" : target.trim()).toLowerCase(Locale.ROOT));
    }

    private static String stripTrailingSlash(String value) {
        String result = value == null || value.isBlank() ? "/" : value;
        while (result.length() > 1 && result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String stripGitSuffix(String value) {
        return value.endsWith(".git") ? value.substring(0, value.length() - 4) : value;
    }
}

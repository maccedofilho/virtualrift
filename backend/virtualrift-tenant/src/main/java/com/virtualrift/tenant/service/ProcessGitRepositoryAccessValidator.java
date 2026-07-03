package com.virtualrift.tenant.service;

import com.virtualrift.common.repository.RepositoryTargetNormalizer;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class ProcessGitRepositoryAccessValidator implements RepositoryAccessValidator {

    private static final Duration ACCESS_TIMEOUT = Duration.ofSeconds(10);

    @Override
    public RepositoryAccessValidationResult validateAccess(String repositoryTarget, Map<String, String> headers) {
        URI repositoryUri = RepositoryTargetNormalizer.toCanonicalRemoteUri(repositoryTarget)
                .orElse(null);
        if (repositoryUri == null) {
            return RepositoryAccessValidationResult.failure("Repository target must be a valid remote repository URL");
        }

        List<String> command = new ArrayList<>();
        command.add("git");
        if (headers != null) {
            headers.forEach((name, value) -> {
                command.add("-c");
                command.add("http.extraHeader=" + name + ": " + value);
            });
        }
        command.add("ls-remote");
        command.add("--symref");
        command.add(repositoryUri.toString());
        command.add("HEAD");

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(ACCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return RepositoryAccessValidationResult.failure("Repository access validation timed out while trying to reach the remote provider");
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() == 0) {
                return RepositoryAccessValidationResult.success();
            }

            return RepositoryAccessValidationResult.failure(classifyFailure(output));
        } catch (IOException exception) {
            return RepositoryAccessValidationResult.failure("Repository access validation could not start the git client");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return RepositoryAccessValidationResult.failure("Repository access validation was interrupted");
        }
    }

    private String classifyFailure(String output) {
        String normalized = output == null ? "" : output.toLowerCase(Locale.ROOT);
        if (normalized.contains("authentication failed")
                || normalized.contains("access denied")
                || normalized.contains("could not read username")
                || normalized.contains("http basic")
                || normalized.contains("403")
                || normalized.contains("401")) {
            return "Repository credentials are invalid or do not have access to the repository";
        }
        if (normalized.contains("repository not found")
                || normalized.contains("not found")
                || normalized.contains("project could not be found")) {
            return "Repository was not found or is not accessible with the configured credentials";
        }
        if (normalized.contains("could not resolve host")
                || normalized.contains("name or service not known")
                || normalized.contains("temporary failure in name resolution")) {
            return "Repository host could not be resolved from the tenant backend";
        }
        return "VirtualRift could not validate repository access during onboarding";
    }
}

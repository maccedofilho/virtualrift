package com.virtualrift.sast.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class ProcessGitClient implements GitClient {

    @Override
    public void cloneRepository(URI repositoryUri, Path destination, Duration timeout, Map<String, String> headers) {
        List<String> command = new ArrayList<>();
        command.add("git");

        if (headers != null) {
            headers.forEach((name, value) -> {
                command.add("-c");
                command.add("http.extraHeader=" + name + ": " + value);
            });
        }

        command.add("clone");
        command.add("--depth=1");
        command.add("--single-branch");
        command.add("--no-tags");
        command.add(repositoryUri.toString());
        command.add(destination.toString());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalArgumentException("Repository clone timed out while contacting the remote provider");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IllegalArgumentException(classifyCloneFailure(output));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Repository clone failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Repository clone was interrupted", e);
        }
    }

    private String classifyCloneFailure(String output) {
        String normalized = output == null ? "" : output.toLowerCase(Locale.ROOT);
        if (normalized.contains("authentication failed")
                || normalized.contains("access denied")
                || normalized.contains("could not read username")
                || normalized.contains("http basic")
                || normalized.contains("403")
                || normalized.contains("401")) {
            return "Repository clone failed because the configured repository credentials were rejected";
        }
        if (normalized.contains("repository not found")
                || normalized.contains("not found")
                || normalized.contains("project could not be found")) {
            return "Repository clone failed because the repository was not found or is not accessible";
        }
        if (normalized.contains("could not resolve host")
                || normalized.contains("name or service not known")
                || normalized.contains("temporary failure in name resolution")) {
            return "Repository clone failed because the repository host could not be resolved";
        }
        return "Repository clone failed while fetching the remote source";
    }
}

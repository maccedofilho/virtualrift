package com.virtualrift.sast.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
                throw new IllegalArgumentException("Git clone timed out");
            }
            if (process.exitValue() != 0) {
                throw new IllegalArgumentException("Git clone failed");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Git clone failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Git clone interrupted", e);
        }
    }
}

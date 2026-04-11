package com.virtualrift.sast.service;

import com.virtualrift.sast.config.SastProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SastTargetResolver Tests")
class SastTargetResolverTest {

    @TempDir
    private Path tempDir;

    private SastProperties properties;
    private RecordingGitClient gitClient;
    private SastTargetResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new SastProperties();
        properties.setWorkspaceRoot(tempDir.resolve("workspace"));
        properties.setCloneTimeoutSeconds(120);
        gitClient = new RecordingGitClient();
        resolver = new SastTargetResolver(properties, gitClient);
    }

    @Test
    @DisplayName("should clone allowed repository into temporary workspace")
    void resolve_quandoRepositorioPermitido_clonaEmWorkspaceTemporario() {
        UUID scanId = UUID.randomUUID();
        Path repositoryPath;

        try (SastTarget target = resolver.resolve("https://github.com/acme/repo.git", scanId, 7)) {
            repositoryPath = target.path();
            assertTrue(Files.exists(repositoryPath.resolve("Example.java")));
            assertEquals(URI.create("https://github.com/acme/repo.git"), gitClient.repositoryUri);
            assertEquals(Duration.ofSeconds(7), gitClient.timeout);
        }

        assertFalse(Files.exists(repositoryPath.getParent()));
    }

    @Test
    @DisplayName("should reject repository scheme outside allowlist")
    void resolve_quandoSchemeNaoPermitido_rejeitaTarget() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve("http://github.com/acme/repo.git", UUID.randomUUID(), 30)
        );

        assertTrue(exception.getMessage().contains("scheme"));
        assertEquals(0, gitClient.cloneCalls);
    }

    @Test
    @DisplayName("should reject repository URL with credentials")
    void resolve_quandoRepositorioTemCredenciais_rejeitaTarget() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve("https://token@github.com/acme/repo.git", UUID.randomUUID(), 30)
        );

        assertTrue(exception.getMessage().contains("credentials"));
        assertEquals(0, gitClient.cloneCalls);
    }

    @Test
    @DisplayName("should reject repository URL pointing to private network")
    void resolve_quandoRepositorioApontaParaRedePrivada_rejeitaTarget() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve("https://192.168.1.10/acme/repo.git", UUID.randomUUID(), 30)
        );

        assertTrue(exception.getMessage().contains("private network"));
        assertEquals(0, gitClient.cloneCalls);
    }

    @Test
    @DisplayName("should reject repository above size limit and cleanup workspace")
    void resolve_quandoRepositorioExcedeTamanho_rejeitaELimpaWorkspace() throws IOException {
        properties.setMaxRepositoryBytes(4);
        gitClient.content = "secret-data";

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve("https://github.com/acme/repo.git", UUID.randomUUID(), 30)
        );

        assertTrue(isWorkspaceEmpty());
    }

    @Test
    @DisplayName("should resolve local path when local targets are enabled")
    void resolve_quandoTargetLocalPermitido_retornaPathLocal() throws IOException {
        Path source = tempDir.resolve("Example.java");
        Files.writeString(source, "class Example {}");

        try (SastTarget target = resolver.resolve(source.toString(), UUID.randomUUID(), 30)) {
            assertEquals(source.toAbsolutePath().normalize(), target.path());
        }

        assertTrue(Files.exists(source));
        assertEquals(0, gitClient.cloneCalls);
    }

    @Test
    @DisplayName("should reject local path when local targets are disabled")
    void resolve_quandoTargetLocalDesabilitado_rejeitaTarget() throws IOException {
        properties.setLocalTargetsEnabled(false);
        Path source = tempDir.resolve("Example.java");
        Files.writeString(source, "class Example {}");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(source.toString(), UUID.randomUUID(), 30)
        );

        assertTrue(exception.getMessage().contains("disabled"));
    }

    private boolean isWorkspaceEmpty() throws IOException {
        if (!Files.exists(properties.getWorkspaceRoot())) {
            return true;
        }
        try (Stream<Path> paths = Files.list(properties.getWorkspaceRoot())) {
            return paths.findAny().isEmpty();
        }
    }

    private static class RecordingGitClient implements GitClient {

        private URI repositoryUri;
        private Duration timeout;
        private int cloneCalls;
        private String content = "class Example {}";

        @Override
        public void cloneRepository(URI repositoryUri, Path destination, Duration timeout) {
            this.repositoryUri = repositoryUri;
            this.timeout = timeout;
            cloneCalls++;
            try {
                Files.createDirectories(destination);
                Files.writeString(destination.resolve("Example.java"), content);
            } catch (IOException e) {
                throw new IllegalArgumentException("test clone failed", e);
            }
        }
    }
}

package com.virtualrift.common.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RepositoryTargetNormalizer Tests")
class RepositoryTargetNormalizerTest {

    @Test
    @DisplayName("should normalize scheme-less repository targets to canonical https urls")
    void toCanonicalRemoteUri_quandoSemEsquema_normalizaParaHttps() {
        URI uri = RepositoryTargetNormalizer.toCanonicalRemoteUri("github.com/acme/platform")
                .orElseThrow();

        assertEquals(URI.create("https://github.com/acme/platform"), uri);
    }

    @Test
    @DisplayName("should normalize ssh short repository targets to canonical https urls")
    void toCanonicalRemoteUri_quandoSshCurto_normalizaParaHttps() {
        URI uri = RepositoryTargetNormalizer.toCanonicalRemoteUri("git@github.com:acme/platform.git")
                .orElseThrow();

        assertEquals(URI.create("https://github.com/acme/platform.git"), uri);
    }

    @Test
    @DisplayName("should collapse browser tree urls to repository root")
    void toCanonicalRemoteUri_quandoUrlDeTreeGithub_retornaRaizDoRepositorio() {
        URI uri = RepositoryTargetNormalizer.toCanonicalRemoteUri("https://github.com/acme/platform/tree/main/src")
                .orElseThrow();

        assertEquals(URI.create("https://github.com/acme/platform"), uri);
    }

    @Test
    @DisplayName("should collapse gitlab browser urls to repository root")
    void toCanonicalRemoteUri_quandoUrlDeBlobGitlab_retornaRaizDoRepositorio() {
        URI uri = RepositoryTargetNormalizer.toCanonicalRemoteUri("https://gitlab.com/acme/security/platform/-/blob/main/pom.xml")
                .orElseThrow();

        assertEquals(URI.create("https://gitlab.com/acme/security/platform"), uri);
    }

    @Test
    @DisplayName("should compare repository targets consistently across https, ssh and browser formats")
    void toComparableKey_quandoFormatosEquivalentes_retornaMesmaChave() {
        String https = RepositoryTargetNormalizer.toComparableKey("https://github.com/acme/platform.git");
        String ssh = RepositoryTargetNormalizer.toComparableKey("git@github.com:acme/platform.git");
        String browser = RepositoryTargetNormalizer.toComparableKey("https://github.com/acme/platform/tree/main");

        assertEquals(https, ssh);
        assertEquals(https, browser);
    }

    @Test
    @DisplayName("should ignore unsupported local paths")
    void toCanonicalRemoteUri_quandoPathLocal_retornaEmpty() {
        assertTrue(RepositoryTargetNormalizer.toCanonicalRemoteUri("./repo").isEmpty());
    }
}

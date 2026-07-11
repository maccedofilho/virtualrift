package com.virtualrift.tenant.service;

import com.virtualrift.common.runtime.RuntimeConfigGuard;
import com.virtualrift.tenant.config.RepositoryCredentialsConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("RepositoryCredentialCipher Tests")
class RepositoryCredentialCipherTest {

    @Test
    @DisplayName("should encrypt and decrypt a repository credential")
    void encrypt_quandoSegredoValido_recuperaValorOriginal() {
        RepositoryCredentialCipher cipher = cipher();

        String encrypted = cipher.encrypt("repository-secret");

        assertNotEquals("repository-secret", encrypted);
        assertEquals("repository-secret", cipher.decrypt(encrypted));
    }

    @Test
    @DisplayName("should reject credentials that exceed the supported size")
    void encrypt_quandoSegredoExcedeLimite_rejeita() {
        RepositoryCredentialCipher cipher = cipher();

        assertThrows(IllegalStateException.class, () -> cipher.encrypt("a".repeat(65_537)));
    }

    private RepositoryCredentialCipher cipher() {
        RepositoryCredentialsConfig config = new RepositoryCredentialsConfig();
        config.setEncryptionKeyBase64(RuntimeConfigGuard.DEFAULT_REPOSITORY_CREDENTIALS_KEY_BASE64);
        return new RepositoryCredentialCipher(config);
    }
}

package com.virtualrift.orchestrator.service;

import com.virtualrift.orchestrator.config.OutboxProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("OutboxPayloadCipher Tests")
class OutboxPayloadCipherTest {

    @Test
    @DisplayName("should encrypt and decrypt payload with authenticated context")
    void encrypt_quandoContextoCorreto_recuperaPayload() {
        OutboxPayloadCipher cipher = cipherWithKey("0123456789abcdef0123456789abcdef");

        String first = cipher.encrypt("secret payload", "event-1");
        String second = cipher.encrypt("secret payload", "event-1");

        assertNotEquals("secret payload", first);
        assertNotEquals(first, second);
        assertEquals("secret payload", cipher.decrypt(first, "event-1"));
    }

    @Test
    @DisplayName("should reject payload when associated event id changes")
    void decrypt_quandoContextoMuda_rejeitaPayload() {
        OutboxPayloadCipher cipher = cipherWithKey("0123456789abcdef0123456789abcdef");
        String encrypted = cipher.encrypt("secret payload", "event-1");

        assertThrows(IllegalStateException.class, () -> cipher.decrypt(encrypted, "event-2"));
    }

    @Test
    @DisplayName("should reject keys with unsupported AES size")
    void constructor_quandoChaveCurta_rejeitaConfiguracao() {
        assertThrows(IllegalStateException.class, () -> cipherWithKey("short"));
    }

    private OutboxPayloadCipher cipherWithKey(String key) {
        OutboxProperties properties = new OutboxProperties();
        properties.setEncryptionKeyBase64(Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8)));
        return new OutboxPayloadCipher(properties);
    }
}

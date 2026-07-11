package com.virtualrift.orchestrator.service;

import com.virtualrift.orchestrator.config.OutboxProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class OutboxPayloadCipher {

    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public OutboxPayloadCipher(OutboxProperties properties) {
        this.secretKey = decodeKey(properties.getEncryptionKeyBase64());
    }

    public String encrypt(String value, String associatedData) {
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not encrypt outbox payload", exception);
        }
    }

    public String decrypt(String value, String associatedData) {
        byte[] combined = Base64.getDecoder().decode(value);
        if (combined.length <= GCM_IV_LENGTH) {
            throw new IllegalStateException("Encrypted outbox payload is invalid");
        }

        byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
        byte[] encrypted = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not decrypt outbox payload", exception);
        }
    }

    private SecretKey decodeKey(String base64Value) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Outbox encryption key must be valid Base64", exception);
        }
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalStateException("Outbox encryption key must be 128, 192 or 256 bits");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}

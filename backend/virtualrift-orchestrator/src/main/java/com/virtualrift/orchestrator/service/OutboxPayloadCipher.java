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
import java.util.Base64;

@Component
public class OutboxPayloadCipher {

    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int MAX_PLAINTEXT_BYTES = 1024 * 1024;
    private static final int MAX_ENCRYPTED_BYTES = MAX_PLAINTEXT_BYTES + (GCM_TAG_BITS / Byte.SIZE);
    private static final String CIPHERTEXT_PREFIX = "v1:";
    private static final String CIPHERTEXT_SEPARATOR = ":";

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public OutboxPayloadCipher(OutboxProperties properties) {
        this.secretKey = decodeKey(properties.getEncryptionKeyBase64());
    }

    public String encrypt(String value, String associatedData) {
        byte[] plaintext = value.getBytes(StandardCharsets.UTF_8);
        if (plaintext.length > MAX_PLAINTEXT_BYTES) {
            throw new IllegalStateException("Outbox payload exceeds the maximum supported size");
        }

        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext);
            return CIPHERTEXT_PREFIX
                    + Base64.getEncoder().encodeToString(iv)
                    + CIPHERTEXT_SEPARATOR
                    + Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not encrypt outbox payload", exception);
        }
    }

    public String decrypt(String value, String associatedData) {
        if (!value.startsWith(CIPHERTEXT_PREFIX)) {
            throw new IllegalStateException("Encrypted outbox payload is invalid");
        }

        String[] components = value.substring(CIPHERTEXT_PREFIX.length()).split(CIPHERTEXT_SEPARATOR, -1);
        if (components.length != 2) {
            throw new IllegalStateException("Encrypted outbox payload is invalid");
        }

        byte[] iv = decodeCiphertextComponent(components[0]);
        byte[] encrypted = decodeCiphertextComponent(components[1]);
        if (iv.length != GCM_IV_LENGTH || encrypted.length > MAX_ENCRYPTED_BYTES) {
            throw new IllegalStateException("Encrypted outbox payload is invalid");
        }

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not decrypt outbox payload", exception);
        }
    }

    private byte[] decodeCiphertextComponent(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Encrypted outbox payload is invalid", exception);
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

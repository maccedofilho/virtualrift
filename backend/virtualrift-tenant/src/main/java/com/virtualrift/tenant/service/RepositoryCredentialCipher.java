package com.virtualrift.tenant.service;

import com.virtualrift.tenant.config.RepositoryCredentialsConfig;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class RepositoryCredentialCipher {

    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int MAX_PLAINTEXT_BYTES = 64 * 1024;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public RepositoryCredentialCipher(RepositoryCredentialsConfig config) {
        this.secretKey = decodeKey(config.getEncryptionKeyBase64());
    }

    public String encrypt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        byte[] plaintext = value.getBytes(StandardCharsets.UTF_8);
        if (plaintext.length > MAX_PLAINTEXT_BYTES) {
            throw new IllegalStateException("Repository credential exceeds the maximum supported size");
        }

        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext);
            return Base64.getEncoder().encodeToString(combineCiphertext(iv, encrypted));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not encrypt repository credentials", exception);
        }
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        byte[] combined = Base64.getDecoder().decode(value);
        if (combined.length <= GCM_IV_LENGTH) {
            throw new IllegalStateException("Encrypted repository credential payload is invalid");
        }

        byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
        byte[] encrypted = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not decrypt repository credentials", exception);
        }
    }

    private SecretKey decodeKey(String base64Value) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Value);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalStateException("Repository credential encryption key must be 128, 192 or 256 bits");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    private byte[] combineCiphertext(byte[] iv, byte[] encrypted) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(iv);
        output.writeBytes(encrypted);
        return output.toByteArray();
    }
}

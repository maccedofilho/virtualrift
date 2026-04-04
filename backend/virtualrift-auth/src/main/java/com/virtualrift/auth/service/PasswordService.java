package com.virtualrift.auth.service;

import com.virtualrift.auth.exception.InvalidPasswordException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.util.Set;

public class PasswordService {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;
    private static final int DEFAULT_RANDOM_LENGTH = 20;

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "password1", "password123", "Password1!",
            "welcome", "welcome1", "Welcome1!", "admin", "admin1",
            "Admin123!", "qwerty", "qwerty1", "letmein", "12345678"
    );

    private final PasswordEncoder encoder;
    private final SecureRandom secureRandom;

    public PasswordService() {
        this.encoder = new BCryptPasswordEncoder();
        this.secureRandom = new SecureRandom();
    }

    public String hash(String password) {
        validate(password);
        return encoder.encode(password);
    }

    public boolean verify(String password, String hash) {
        if (password == null) {
            throw new InvalidPasswordException("password cannot be null");
        }
        if (hash == null) {
            throw new InvalidPasswordException("hash cannot be null");
        }

        try {
            return encoder.matches(password, hash);
        } catch (Exception e) {
            return false;
        }
    }

    public void validate(String password) {
        if (password == null) {
            throw new InvalidPasswordException("password cannot be null");
        }
        if (password.isBlank()) {
            throw new InvalidPasswordException("password cannot be blank");
        }
        if (password.length() < MIN_LENGTH) {
            throw new InvalidPasswordException("password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.length() > MAX_LENGTH) {
            throw new InvalidPasswordException("password must not exceed " + MAX_LENGTH + " characters");
        }

        if (!password.matches(".*[A-Z].*")) {
            throw new InvalidPasswordException("password must contain at least one uppercase letter");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new InvalidPasswordException("password must contain at least one lowercase letter");
        }
        if (!password.matches(".*\\d.*")) {
            throw new InvalidPasswordException("password must contain at least one digit");
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            throw new InvalidPasswordException("password must contain at least one special character");
        }

        if (COMMON_PASSWORDS.contains(password.toLowerCase())) {
            throw new InvalidPasswordException("password is too common");
        }
    }

    public String generateRandom() {
        return generateRandom(DEFAULT_RANDOM_LENGTH);
    }

    public String generateRandom(int length) {
        if (length < MIN_LENGTH) {
            length = MIN_LENGTH;
        }

        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String special = "!@#$%^&*()_+-=[]{}|";
        String all = upper + lower + digits + special;

        StringBuilder password = new StringBuilder(length);

        password.append(upper.charAt(secureRandom.nextInt(upper.length())));
        password.append(lower.charAt(secureRandom.nextInt(lower.length())));
        password.append(digits.charAt(secureRandom.nextInt(digits.length())));
        password.append(special.charAt(secureRandom.nextInt(special.length())));

        for (int i = 4; i < length; i++) {
            password.append(all.charAt(secureRandom.nextInt(all.length())));
        }

        return shuffle(password.toString());
    }

    private String shuffle(String input) {
        char[] characters = input.toCharArray();
        for (int i = characters.length - 1; i > 0; i--) {
            int j = secureRandom.nextInt(i + 1);
            char temp = characters[i];
            characters[i] = characters[j];
            characters[j] = temp;
        }
        return new String(characters);
    }
}

package com.virtualrift.auth.service;

import java.time.Instant;

public interface TokenDenylist {
    void add(String token, Instant revokedAt);
    boolean isRevoked(String token);
    void remove(String token);
    void cleanup();
}

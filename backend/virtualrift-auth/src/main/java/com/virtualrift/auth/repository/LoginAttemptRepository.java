package com.virtualrift.auth.repository;

import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface LoginAttemptRepository {

    void recordFailedAttempt(String email);

    void clearFailedAttempts(String email);

    int getFailedAttempts(String email);

    void recordSuccessfulAttempt(String email);

    Instant getLastAttemptTime(String email);
}

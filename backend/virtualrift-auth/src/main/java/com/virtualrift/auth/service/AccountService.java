package com.virtualrift.auth.service;

import com.virtualrift.auth.dto.AccountProfileResponse;
import com.virtualrift.auth.exception.InvalidTokenException;
import com.virtualrift.auth.model.User;
import com.virtualrift.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AccountService {

    private final UserRepository userRepository;

    public AccountService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AccountProfileResponse getProfile(UUID userId, UUID tenantId) {
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new InvalidTokenException("Authenticated user was not found"));

        return new AccountProfileResponse(
                user.id(),
                user.email(),
                user.tenantId(),
                user.status(),
                user.roles(),
                user.createdAt(),
                user.updatedAt()
        );
    }
}

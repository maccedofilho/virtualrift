package com.virtualrift.auth.repository;

import com.virtualrift.auth.model.User;
import com.virtualrift.auth.model.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);
}

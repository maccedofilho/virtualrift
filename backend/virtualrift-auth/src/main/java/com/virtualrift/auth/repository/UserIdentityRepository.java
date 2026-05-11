package com.virtualrift.auth.repository;

import com.virtualrift.auth.model.OAuthProvider;
import com.virtualrift.auth.model.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

    Optional<UserIdentity> findByProviderAndProviderSubject(OAuthProvider provider, String providerSubject);

    boolean existsByUserIdAndProvider(UUID userId, OAuthProvider provider);
}

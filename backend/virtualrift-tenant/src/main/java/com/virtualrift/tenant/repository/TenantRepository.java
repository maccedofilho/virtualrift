package com.virtualrift.tenant.repository;

import com.virtualrift.tenant.model.Plan;
import com.virtualrift.tenant.model.Tenant;
import com.virtualrift.tenant.model.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    boolean existsBySlug(String slug);

    @Query("SELECT t FROM Tenant t WHERE t.slug = :slug AND t.status = :status")
    Optional<Tenant> findBySlugAndStatus(@Param("slug") String slug, @Param("status") TenantStatus status);

    @Query("SELECT t FROM Tenant t WHERE t.slug = :slug")
    Optional<Tenant> findBySlug(@Param("slug") String slug);

    @Query("SELECT t.plan FROM Tenant t WHERE t.id = :id")
    Optional<Plan> findPlanById(@Param("id") UUID id);
}

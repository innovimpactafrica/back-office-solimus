package com.example.solimus.repositories;

import com.example.solimus.entities.ProviderWalletRecharge;
import com.example.solimus.enums.ProofStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProviderWalletRechargeRepository extends JpaRepository<ProviderWalletRecharge, Long> {

    // Verrou : deux validations simultanées ne créditent jamais deux fois la même recharge
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ProviderWalletRecharge r WHERE r.id = :id")
    Optional<ProviderWalletRecharge> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"provider", "reviewedBy"})
    @Query("SELECT r FROM ProviderWalletRecharge r WHERE r.id = :id")
    Optional<ProviderWalletRecharge> findWithProviderById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"provider", "reviewedBy"})
    @Query("SELECT r FROM ProviderWalletRecharge r WHERE r.provider.id = :providerId "
            + "AND (:status IS NULL OR r.status = :status) ORDER BY r.submittedAt DESC, r.id DESC")
    Page<ProviderWalletRecharge> findForProvider(@Param("providerId") Long providerId,
                                                 @Param("status") ProofStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"provider", "reviewedBy"})
    @Query("SELECT r FROM ProviderWalletRecharge r WHERE (:status IS NULL OR r.status = :status) "
            + "AND (:search IS NULL OR LOWER(r.reference) LIKE :search OR LOWER(r.provider.email) LIKE :search "
            + "OR LOWER(r.provider.phone) LIKE :search "
            + "OR LOWER(CONCAT(r.provider.firstName, ' ', r.provider.lastName)) LIKE :search) "
            + "ORDER BY r.submittedAt DESC, r.id DESC")
    Page<ProviderWalletRecharge> searchForAdmin(@Param("status") ProofStatus status, @Param("search") String search,
                                                Pageable pageable);

    long countByStatus(ProofStatus status);

    long countByProviderIdAndStatus(Long providerId, ProofStatus status);
}

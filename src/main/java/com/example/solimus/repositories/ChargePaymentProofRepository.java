package com.example.solimus.repositories;

import com.example.solimus.entities.ChargePaymentProof;
import com.example.solimus.enums.ChargeType;
import com.example.solimus.enums.ProofStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChargePaymentProofRepository extends JpaRepository<ChargePaymentProof, Long> {

    boolean existsByChargeCallItemIdAndStatus(Long chargeCallItemId, ProofStatus status);

    boolean existsByExceptionalCallItemIdAndStatus(Long exceptionalCallItemId, ProofStatus status);

    List<ChargePaymentProof> findByChargeCallItemIdOrderBySubmittedAtDesc(Long chargeCallItemId);

    List<ChargePaymentProof> findByExceptionalCallItemIdOrderBySubmittedAtDesc(Long exceptionalCallItemId);

    // Verrou : deux validations simultanées (deux onglets, double clic) ne traitent jamais la même preuve
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ChargePaymentProof p WHERE p.id = :id")
    Optional<ChargePaymentProof> findByIdForUpdate(@Param("id") Long id);

    // Preuves des résidences d'un syndic, filtres facultatifs, plus récentes d'abord
    @Query(value = "SELECT p FROM ChargePaymentProof p JOIN p.coOwner o " +
            "WHERE p.residence.syndic.id = :syndicId " +
            "AND (:status IS NULL OR p.status = :status) " +
            "AND (:residenceId IS NULL OR p.residence.id = :residenceId) " +
            "AND (:chargeType IS NULL OR p.chargeType = :chargeType) " +
            "AND (:search IS NULL OR LOWER(o.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(o.lastName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(p.reference) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "ORDER BY p.submittedAt DESC",
            countQuery = "SELECT COUNT(p) FROM ChargePaymentProof p JOIN p.coOwner o " +
            "WHERE p.residence.syndic.id = :syndicId " +
            "AND (:status IS NULL OR p.status = :status) " +
            "AND (:residenceId IS NULL OR p.residence.id = :residenceId) " +
            "AND (:chargeType IS NULL OR p.chargeType = :chargeType) " +
            "AND (:search IS NULL OR LOWER(o.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(o.lastName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(p.reference) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<ChargePaymentProof> searchForSyndic(@Param("syndicId") Long syndicId,
                                             @Param("status") ProofStatus status,
                                             @Param("residenceId") Long residenceId,
                                             @Param("chargeType") ChargeType chargeType,
                                             @Param("search") String search,
                                             Pageable pageable);

    @Query("SELECT COUNT(p) FROM ChargePaymentProof p WHERE p.residence.syndic.id = :syndicId AND p.status = :status")
    long countBySyndicAndStatus(@Param("syndicId") Long syndicId, @Param("status") ProofStatus status);
}

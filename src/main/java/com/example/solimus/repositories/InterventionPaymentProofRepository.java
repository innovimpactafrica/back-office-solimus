package com.example.solimus.repositories;

import com.example.solimus.entities.InterventionPaymentProof;
import com.example.solimus.enums.PaymentType;
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

public interface InterventionPaymentProofRepository extends JpaRepository<InterventionPaymentProof, Long> {

    boolean existsByInterventionRequestIdAndTypeAndStatus(Long interventionRequestId, PaymentType type, ProofStatus status);

    List<InterventionPaymentProof> findByInterventionRequestIdOrderBySubmittedAtDesc(Long interventionRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM InterventionPaymentProof p WHERE p.id = :id")
    Optional<InterventionPaymentProof> findByIdForUpdate(@Param("id") Long id);

    // Preuves reçues par un prestataire, filtre de statut facultatif, plus récentes d'abord
    @Query(value = "SELECT p FROM InterventionPaymentProof p JOIN p.interventionRequest i " +
            "WHERE p.provider.id = :providerId " +
            "AND (:status IS NULL OR p.status = :status) " +
            "AND (:search IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(p.reference) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "ORDER BY p.submittedAt DESC",
            countQuery = "SELECT COUNT(p) FROM InterventionPaymentProof p JOIN p.interventionRequest i " +
            "WHERE p.provider.id = :providerId " +
            "AND (:status IS NULL OR p.status = :status) " +
            "AND (:search IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(p.reference) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<InterventionPaymentProof> searchForProvider(@Param("providerId") Long providerId,
                                                     @Param("status") ProofStatus status,
                                                     @Param("search") String search,
                                                     Pageable pageable);

    long countByProviderIdAndStatus(Long providerId, ProofStatus status);
}

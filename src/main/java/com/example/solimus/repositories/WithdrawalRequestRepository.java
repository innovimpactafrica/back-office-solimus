package com.example.solimus.repositories;

import com.example.solimus.entities.ProviderWithdrawalRequest;
import com.example.solimus.enums.WithdrawalStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WithdrawalRequestRepository extends JpaRepository<ProviderWithdrawalRequest, Long> {

    List<ProviderWithdrawalRequest> findAllByProviderIdOrderByCreatedAtDesc(Long providerId);

    // ============================================================
    // ADMIN — DEMANDES DE RETRAIT (tous prestataires confondus)
    // ============================================================

    // Compte toutes les demandes ayant ce statut, tous prestataires confondus
    long countByStatus(WithdrawalStatus status);

    // Compte les demandes créées dans une période donnée, tous statuts — "Total Demandes" (évolution)
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // Compte les demandes ayant ce statut ET créées dans une période donnée — "Demande Validée" (évolution)
    long countByStatusAndCreatedAtBetween(WithdrawalStatus status, LocalDateTime start, LocalDateTime end);

    // Compte les demandes ayant ce statut ET traitées (processedAt) dans une période donnée —
    // "Demande refusée" (fenêtre des 30 derniers jours)
    long countByStatusAndProcessedAtBetween(WithdrawalStatus status, LocalDateTime start, LocalDateTime end);

    // Somme de toutes les demandes ayant ce statut, sans limite de période — "Montant Total" (COMPLETED)
    @Query("SELECT COALESCE(SUM(w.amount), 0) FROM ProviderWithdrawalRequest w WHERE w.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") WithdrawalStatus status);

    // Somme des demandes COMPLETED d'un prestataire précis, sur une période donnée — "Retiré ce mois" (Bloc 3)
    @Query("SELECT COALESCE(SUM(w.amount), 0) FROM ProviderWithdrawalRequest w " +
           "WHERE w.provider.id = :providerId AND w.status = 'COMPLETED' " +
           "AND w.processedAt BETWEEN :start AND :end")
    BigDecimal sumCompletedAmountInPeriod(@Param("providerId") Long providerId,
                                          @Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    // Les N dernières demandes COMPLETED de ce prestataire, en excluant la demande actuellement
    // consultée — "Derniers retraits effectués" (Bloc 3)
    List<ProviderWithdrawalRequest> findByProviderIdAndStatusAndIdNotOrderByProcessedAtDesc(
            Long providerId, WithdrawalStatus status, Long excludedId, Pageable pageable);

    // ============================================================
    // SOLDE CALCULÉ
    // ============================================================

    // Somme des retraits PENDING ou COMPLETED d'un prestataire — montant "réservé", à soustraire du
    // total encaissé pour obtenir le solde disponible.
    // ATTENTION : ce principe (réserver les PENDING) a été volontairement abandonné côté syndic —
    // voir SyndicTreasuryService.getAvailableBalance, qui ne déduit plus que les retraits COMPLETED —
    // ce même correctif n'a pas encore été appliqué ici côté prestataire, à faire si besoin
    @Query("SELECT COALESCE(SUM(w.amount), 0) FROM ProviderWithdrawalRequest w " +
           "WHERE w.provider.id = :providerId " +
           "AND w.status IN (com.example.solimus.enums.WithdrawalStatus.PENDING, com.example.solimus.enums.WithdrawalStatus.COMPLETED)")
    BigDecimal sumPendingAndCompletedByProviderId(@Param("providerId") Long providerId);

    // Somme des retraits encore PENDING d'un prestataire — "solde en attente de validation" affiché au prestataire
    @Query("SELECT COALESCE(SUM(w.amount), 0) FROM ProviderWithdrawalRequest w " +
           "WHERE w.provider.id = :providerId AND w.status = 'PENDING'")
    BigDecimal sumPendingByProviderId(@Param("providerId") Long providerId);
}

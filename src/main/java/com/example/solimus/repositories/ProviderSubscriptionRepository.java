package com.example.solimus.repositories;

import com.example.solimus.entities.ProviderSubscription;
import com.example.solimus.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderSubscriptionRepository extends JpaRepository<ProviderSubscription, Long> {

    // Récupère le dernier abonnement d'un prestataire (le plus récent selon endDate)
    // → utilisé avant de créer un nouveau paiement, pour vérifier qu'il n'a pas déjà un abonnement actif
    Optional<ProviderSubscription> findFirstByProviderIdOrderByEndDateDesc(Long providerId);

    // Récupère tous les abonnements d'un prestataire, du plus récent au plus ancien
    // → utilisé pour l'écran "Mon abonnement" (carte actuelle + historique des paiements)
    Page<ProviderSubscription> findByProviderIdOrderByStartDateDesc(Long providerId, Pageable pageable);

    // Récupère l'abonnement correspondant à une référence de transaction TouchPay (SUB-xxx)
    // → utilisé par le bridge et le callback pour retrouver la bonne ligne
    Optional<ProviderSubscription> findByTransactionRef(String transactionRef);

    // Récupère tous les abonnements ACTIVE dont la date de fin est dépassée
    // → utilisé par le scheduler horaire pour les faire passer en EXPIRED
    List<ProviderSubscription> findByStatusAndEndDateBefore(SubscriptionStatus status, LocalDateTime dateTime);

    // Récupère tous les paiements PENDING créés avant un certain seuil de temps
    // → utilisé par le scheduler chaque minute pour les faire passer en FAILED après 5 min
    List<ProviderSubscription> findByStatusAndCreatedAtBefore(SubscriptionStatus status, LocalDateTime dateTime);

    // Compte, directement en base, les abonnements prestataires réellement actifs en ce moment
    // (statut ACTIVE ET date de fin pas encore dépassée)
    @Query("SELECT COUNT(s) FROM ProviderSubscription s WHERE s.status = 'ACTIVE' AND s.endDate > :now")
    long countCurrentlyActive(@Param("now") LocalDateTime now);

    // Exclut ceux ayant déjà un nouvel abonnement actif
    @Query("SELECT COUNT(s) FROM ProviderSubscription s " +
           "WHERE s.status = 'EXPIRED' " +
           "AND NOT EXISTS (" +
           "  SELECT 1 FROM ProviderSubscription s2 " +
           "  WHERE s2.provider.id = s.provider.id " +
           "  AND s2.status = 'ACTIVE'" +
           ")")
    long countCurrentlyExpiredWithoutRenewal();

    // Nombre d'abonnements à renouveler bientôt
    @Query("SELECT COUNT(s) FROM ProviderSubscription s " +
           "WHERE s.status = 'ACTIVE' " +
           "AND s.endDate BETWEEN :now AND :limit")
    long countToRenewSoon(@Param("now") LocalDateTime now, @Param("limit") LocalDateTime limit);

    // Somme des montants payés sur une période donnée
    @Query("SELECT COALESCE(SUM(s.amountPaid), 0) FROM ProviderSubscription s " +
           "WHERE s.createdAt BETWEEN :start AND :end")
    java.math.BigDecimal sumAmountPaidInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Nombre d'abonnements arrivés à échéance sur une période
    @Query("SELECT COUNT(s) FROM ProviderSubscription s WHERE s.endDate BETWEEN :start AND :end")
    long countExpiredInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Renouvelé = même utilisateur a un abonnement qui débute après cette fin
    @Query("SELECT COUNT(s) FROM ProviderSubscription s " +
           "WHERE s.endDate BETWEEN :start AND :end " +
           "AND EXISTS (" +
           "  SELECT 1 FROM ProviderSubscription s2 " +
           "  WHERE s2.provider.id = s.provider.id " +
           "  AND s2.startDate >= s.endDate " +
           "  AND s2.id != s.id" +
           ")")
    long countRenewedInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Compte combien étaient actifs à une date précise dans le passé
    @Query("SELECT COUNT(s) FROM ProviderSubscription s " +
           "WHERE s.startDate <= :asOfDate " +
           "AND s.endDate > :asOfDate")
    long countActiveAsOf(@Param("asOfDate") LocalDateTime asOfDate);

    // Compte combien étaient expirés sans renouvellement à une date précise dans le passé
    @Query("SELECT COUNT(s) FROM ProviderSubscription s " +
           "WHERE s.endDate <= :asOfDate " +
           "AND NOT EXISTS (" +
           "  SELECT 1 FROM ProviderSubscription s2 " +
           "  WHERE s2.provider.id = s.provider.id " +
           "  AND s2.startDate <= :asOfDate " +
           "  AND s2.endDate > :asOfDate" +
           ")")
    long countExpiredWithoutRenewalAsOf(@Param("asOfDate") LocalDateTime asOfDate);
}
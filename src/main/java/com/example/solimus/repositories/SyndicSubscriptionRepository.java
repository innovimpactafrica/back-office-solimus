package com.example.solimus.repositories;

import com.example.solimus.entities.SyndicSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
public interface SyndicSubscriptionRepository extends JpaRepository<SyndicSubscription, Long> {

    // Compte les abonnés actuels sur une formule précise
    long countBySyndicPlanId(Long syndicPlanId);

    // Nombre d'abonnements actifs
    @Query("SELECT COUNT(s) FROM SyndicSubscription s WHERE s.status = 'ACTIVE' AND s.endDate > :now")
    long countCurrentlyActive(@Param("now") LocalDateTime now);

    // Exclut ceux ayant déjà un nouvel abonnement actif
    @Query("SELECT COUNT(s) FROM SyndicSubscription s " +
           "WHERE s.status = 'EXPIRED' " +
           "AND NOT EXISTS (" +
           "  SELECT 1 FROM SyndicSubscription s2 " +
           "  WHERE s2.syndic.id = s.syndic.id " +
           "  AND s2.status = 'ACTIVE'" +
           ")")
    long countCurrentlyExpiredWithoutRenewal();

    // Nombre d'abonnements à renouveler bientôt
    @Query("SELECT COUNT(s) FROM SyndicSubscription s " +
           "WHERE s.status = 'ACTIVE' " +
           "AND s.endDate BETWEEN :now AND :limit")
    long countToRenewSoon(@Param("now") LocalDateTime now, @Param("limit") LocalDateTime limit);

    // Somme des montants payés sur une période donnée
    @Query("SELECT COALESCE(SUM(s.amountPaid), 0) FROM SyndicSubscription s " +
           "WHERE s.createdAt BETWEEN :start AND :end")
    BigDecimal sumAmountPaidInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Nombre d'abonnements arrivés à échéance sur une période
    @Query("SELECT COUNT(s) FROM SyndicSubscription s WHERE s.endDate BETWEEN :start AND :end")
    long countExpiredInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Renouvelé = même utilisateur a un abonnement qui débute après cette fin
    @Query("SELECT COUNT(s) FROM SyndicSubscription s " +
           "WHERE s.endDate BETWEEN :start AND :end " +
           "AND EXISTS (" +
           "  SELECT 1 FROM SyndicSubscription s2 " +
           "  WHERE s2.syndic.id = s.syndic.id " +
           "  AND s2.startDate >= s.endDate " +
           "  AND s2.id != s.id" +
           ")")
    long countRenewedInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Compte combien étaient actifs à une date précise dans le passé
    @Query("SELECT COUNT(s) FROM SyndicSubscription s " +
           "WHERE s.startDate <= :asOfDate " +
           "AND s.endDate > :asOfDate")
    long countActiveAsOf(@Param("asOfDate") LocalDateTime asOfDate);

    // Compte combien étaient expirés sans renouvellement à une date précise dans le passé
    @Query("SELECT COUNT(s) FROM SyndicSubscription s " +
           "WHERE s.endDate <= :asOfDate " +
           "AND NOT EXISTS (" +
           "  SELECT 1 FROM SyndicSubscription s2 " +
           "  WHERE s2.syndic.id = s.syndic.id " +
           "  AND s2.startDate <= :asOfDate " +
           "  AND s2.endDate > :asOfDate" +
           ")")
    long countExpiredWithoutRenewalAsOf(@Param("asOfDate") LocalDateTime asOfDate);
}
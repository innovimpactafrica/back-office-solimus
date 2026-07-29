package com.example.solimus.repositories;

import com.example.solimus.entities.SyndicSubscription;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
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
public interface SyndicSubscriptionRepository extends JpaRepository<SyndicSubscription, Long> {

    // Retrouve l'abonnement PENDING créé à l'initiation du paiement, à partir de la référence TouchPay
    Optional<SyndicSubscription> findByTransactionRef(String transactionRef);

    // Abonnements encore PENDING créés avant ce seuil — utilisé par le scheduler d'expiration
    List<SyndicSubscription> findByStatusAndCreatedAtBefore(SubscriptionStatus status, LocalDateTime dateTime);

    // Abonnements FAILED dont le syndic n'a jamais été activé (aucun paiement n'a jamais abouti pour ce compte) —
    // ce sont des comptes orphelins à purger pour libérer l'email/téléphone
    //s.syndic.status désigne le champ status de User(userSyndic) (Compte utilisateur)
    @Query("SELECT s FROM SyndicSubscription s WHERE s.status = 'FAILED' AND s.syndic.status = 'PENDING'")
    List<SyndicSubscription> findFailedWithNeverActivatedSyndic();

    // Abonnement le plus récent d'un syndic (par date de fin), TOUS statuts confondus — utilisé
    // uniquement en repli sur la page "Mon abonnement" quand aucun abonnement ACTIVE n'existe
    // (ex: tout est expiré/annulé). Ne JAMAIS utiliser ceci pour vérifier un accès : la date de fin
    // la plus lointaine peut appartenir à un abonnement annulé (ex: un ancien abonnement annuel
    // remplacé par un nouveau mensuel finit "plus tard" alors qu'il n'est plus actif).
    Optional<SyndicSubscription> findFirstBySyndicIdOrderByEndDateDesc(Long syndicId);

    // L'abonnement réellement actif d'un syndic — au plus un seul à la fois (l'ancien est annulé
    // dès qu'un nouveau devient actif). C'est la SEULE requête à utiliser pour vérifier un accès
    // (features, limites) ou afficher l'abonnement en cours.
    Optional<SyndicSubscription> findFirstBySyndicIdAndStatus(Long syndicId, SubscriptionStatus status);

    // Vérifie si le syndic a déjà une tentative de paiement PENDING en cours — utilisé pour bloquer
    // l'initiation d'un nouveau paiement tant que l'ancien n'a pas expiré (5 min) ou été confirmé
    boolean existsBySyndicIdAndStatus(Long syndicId, SubscriptionStatus status);

    // Historique paginé des abonnements d'un syndic (paiements passés), du plus récent au plus ancien
    Page<SyndicSubscription> findBySyndicIdOrderByCreatedAtDesc(Long syndicId, Pageable pageable);

    // Tout l'historique d'un syndic, du plus ancien au plus récent — utilisé pour reconstituer le
    // cycle de vie (souscription initiale, renouvellements, changements de formule) sur la page détail admin
    List<SyndicSubscription> findBySyndicIdOrderByCreatedAtAsc(Long syndicId);

    // Abonnements ACTIVE dont la date de fin est déjà dépassée — utilisé par le scheduler d'expiration
    List<SyndicSubscription> findByStatusAndEndDateBefore(SubscriptionStatus status, LocalDateTime dateTime);

    // Abonnements ACTIVE dont la date de fin tombe dans la journée ciblée — utilisé par le rappel
    // d'expiration (J-10), peu importe qu'il soit mensuel ou annuel
    @Query("SELECT s FROM SyndicSubscription s WHERE s.status = 'ACTIVE' AND s.endDate BETWEEN :startOfDay AND :endOfDay")
    List<SyndicSubscription> findActiveExpiringBetween(@Param("startOfDay") LocalDateTime startOfDay,
                                                         @Param("endOfDay") LocalDateTime endOfDay);

    // Abonnement(s) encore ACTIVE d'un syndic, autre que celui-ci — utilisé au changement de formule
    // (SYR-) pour annuler l'ancien abonnement remplacé par le nouveau
    @Query("SELECT s FROM SyndicSubscription s WHERE s.syndic.id = :syndicId AND s.status = 'ACTIVE' AND s.id <> :excludeId")
    List<SyndicSubscription> findActiveBySyndicIdExcluding(@Param("syndicId") Long syndicId, @Param("excludeId") Long excludeId);

    // Compte les syndics DISTINCTS ayant actuellement (statut ACTIVE et date de fin non dépassée)
    // cette formule — jamais un simple COUNT(*) des lignes, qui compterait aussi les tentatives
    // passées/échouées et les renouvellements d'un même syndic plusieurs fois
    @Query("SELECT COUNT(DISTINCT s.syndic.id) FROM SyndicSubscription s " +
           "WHERE s.syndicPlan.id = :syndicPlanId AND s.status = 'ACTIVE' AND s.endDate > CURRENT_TIMESTAMP")
    long countBySyndicPlanId(@Param("syndicPlanId") Long syndicPlanId);

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

    // Les N derniers abonnements ayant ce statut de paiement précis (COMPLETED ou FAILED), toutes
    // syndics confondus — utilisé pour assembler le flux "Activité récente" du dashboard admin
    List<SyndicSubscription> findByPaymentStatusOrderByCreatedAtDesc(PaymentStatus paymentStatus, Pageable pageable);

    // Vérifie si ce syndic avait déjà un abonnement avant celui-ci — permet de distinguer une
    // souscription initiale ("Nouveau syndic enregistré") d'un renouvellement ("Abonnement renouvelé")
    boolean existsBySyndicIdAndCreatedAtBefore(Long syndicId, LocalDateTime before);
}
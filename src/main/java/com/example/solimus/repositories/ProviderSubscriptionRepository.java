package com.example.solimus.repositories;

import com.example.solimus.entities.ProviderSubscription;
import com.example.solimus.enums.PaymentStatus;
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

    // Abonnement le plus récent d'un prestataire (par date de fin), TOUS statuts confondus — à
    // utiliser uniquement en repli d'affichage quand aucun abonnement ACTIVE n'existe. Ne JAMAIS
    // utiliser ceci pour vérifier un accès : la date de fin la plus lointaine peut appartenir à un
    // abonnement annulé (même piège que côté syndic, déjà corrigé).
    Optional<ProviderSubscription> findFirstByProviderIdOrderByEndDateDesc(Long providerId);

    // L'abonnement réellement actif d'un prestataire — au plus un seul à la fois. C'est la SEULE
    // requête à utiliser pour vérifier un accès (SubscriptionFilter) ou afficher l'abonnement en cours.
    Optional<ProviderSubscription> findFirstByProviderIdAndStatus(Long providerId, SubscriptionStatus status);

    // Compte les prestataires DISTINCTS ayant actuellement (statut ACTIVE et date de fin non dépassée)
    // cette formule — jamais un simple COUNT(*) des lignes, qui compterait aussi les tentatives
    // passées/échouées et les renouvellements d'un même prestataire plusieurs fois
    @Query("SELECT COUNT(DISTINCT s.provider.id) FROM ProviderSubscription s " +
           "WHERE s.providerPlan.id = :providerPlanId AND s.status = 'ACTIVE' AND s.endDate > CURRENT_TIMESTAMP")
    long countByProviderPlanId(@Param("providerPlanId") Long providerPlanId);

    // Récupère tous les abonnements d'un prestataire, du plus récent au plus ancien
    // → utilisé pour l'écran "Mon abonnement" (carte actuelle + historique des paiements)
    Page<ProviderSubscription> findByProviderIdOrderByStartDateDesc(Long providerId, Pageable pageable);

    // Historique paginé des abonnements d'un prestataire (paiements passés), vu par l'admin —
    // trié par date de création, comme côté syndic
    Page<ProviderSubscription> findByProviderIdOrderByCreatedAtDesc(Long providerId, Pageable pageable);

    // Tout l'historique d'un prestataire, du plus ancien au plus récent — utilisé pour reconstituer
    // le cycle de vie sur la page détail admin
    List<ProviderSubscription> findByProviderIdOrderByCreatedAtAsc(Long providerId);

    // Récupère l'abonnement correspondant à une référence de transaction TouchPay (SUB-xxx)
    // → utilisé par le bridge et le callback pour retrouver la bonne ligne
    Optional<ProviderSubscription> findByTransactionRef(String transactionRef);

    // Vérifie si le prestataire a déjà une tentative de paiement PENDING en cours — utilisé pour
    // bloquer l'initiation d'un nouveau paiement tant que l'ancien n'a pas expiré (5 min) ou été confirmé
    boolean existsByProviderIdAndStatus(Long providerId, SubscriptionStatus status);

    // Abonnement(s) encore ACTIVE d'un prestataire, autre que celui-ci — utilisé à la confirmation
    // d'un nouveau paiement pour annuler l'ancien abonnement remplacé par le nouveau (jamais deux
    // abonnements ACTIVE en même temps pour le même prestataire)
    @Query("SELECT s FROM ProviderSubscription s WHERE s.provider.id = :providerId AND s.status = 'ACTIVE' AND s.id <> :excludeId")
    List<ProviderSubscription> findActiveByProviderIdExcluding(@Param("providerId") Long providerId, @Param("excludeId") Long excludeId);

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

    // Somme de tous les montants payés, sans limite de période — "Revenus" du dashboard Admin > Prestataires
    @Query("SELECT COALESCE(SUM(s.amountPaid), 0) FROM ProviderSubscription s")
    java.math.BigDecimal sumAmountPaidTotal();

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

    // Les N derniers abonnements ayant ce statut de paiement précis (COMPLETED ou FAILED), tous
    // prestataires confondus — utilisé pour assembler le flux "Activité récente" du dashboard admin
    List<ProviderSubscription> findByPaymentStatusOrderByCreatedAtDesc(PaymentStatus paymentStatus, Pageable pageable);

    // Vérifie si ce prestataire avait déjà un abonnement avant celui-ci — permet de distinguer une
    // souscription initiale d'un renouvellement
    boolean existsByProviderIdAndCreatedAtBefore(Long providerId, LocalDateTime before);
}
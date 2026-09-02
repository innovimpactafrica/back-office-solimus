package com.example.solimus.repositories;

import com.example.solimus.entities.ChargeCallItem;
import com.example.solimus.entities.User;
import com.example.solimus.enums.ChargeItemPaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChargeCallItemRepository extends JpaRepository<ChargeCallItem, Long> {

    /**
     * Récupère les charges courantes d'un copropriétaire
     */
    List<ChargeCallItem> findByCoOwnerId(Long coOwnerId);

    /**
     * Lister toutes les lignes d'appel de charges pour une résidence
     * Triées du plus récent au plus ancien (par ChargeCall)
     */
    @Query("SELECT cci FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE b.residence.id = :residenceId " +
           "ORDER BY cc.year DESC, cc.periodNumber DESC")
    Page<ChargeCallItem> findByResidenceId(@Param("residenceId") Long residenceId, Pageable pageable);

    /**
     * Compter les lignes d'appel de charges pour un copropriétaire et une résidence.
     */
    @Query("SELECT COUNT(cci) FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE b.residence.id = :residenceId AND cci.coOwner.id = :coOwnerId")
    long countByCoOwnerIdAndResidenceId(@Param("coOwnerId") Long coOwnerId, @Param("residenceId") Long residenceId);

    // ===== DASHBOARD SYNDIC - STATS GLOBALES =====

    /**
     * Somme des montants payés pour un syndic (trésorerie globale)
     */
    @Query("SELECT COALESCE(SUM(cci.paidAmount), 0) FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE b.residence.syndic = :syndic")
    BigDecimal sumPaidAmountBySyndic(@Param("syndic") User syndic);

    /**
     * Compter les résidences avec au moins un impayé pour un syndic
     */
    @Query("SELECT COUNT(DISTINCT b.residence.id) FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE b.residence.syndic = :syndic " +
           "AND cci.status != :paidStatus")
    long countResidencesWithUnpaidBySyndic(@Param("syndic") User syndic, @Param("paidStatus") ChargeItemPaymentStatus paidStatus);

    // ===== CALCULS PAR RÉSIDENCE =====

    /**
     * Somme des quote-parts pour une résidence (montant total dû)
     */
    @Query("SELECT COALESCE(SUM(cci.quotePart), 0) FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE b.residence.id = :residenceId")
    BigDecimal sumQuotePartByResidenceId(@Param("residenceId") Long residenceId);

    /**
     * Somme des montants payés pour une résidence (trésorerie)
     */
    @Query("SELECT COALESCE(SUM(cci.paidAmount), 0) FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE b.residence.id = :residenceId")
    BigDecimal sumPaidAmountByResidenceId(@Param("residenceId") Long residenceId);

    // ===== CALCULS PAR COPROPRIÉTAIRE (LISTE COPROPRIÉTAIRES) =====

    /**
     * Compter les ChargeCallItems en retard pour un copropriétaire, restreint au syndic
     * Retard = dueDate < aujourd'hui ET status != PAID
     */
    @Query("SELECT COUNT(cci) FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE cci.coOwner.id = :coOwnerId " +
           "AND b.residence.syndic.id = :syndicId " +
           "AND cc.dueDate < CURRENT_DATE " +
           "AND cci.status != 'PAID'")
    long countLateItemsByCoOwnerAndSyndic(@Param("coOwnerId") Long coOwnerId, @Param("syndicId") Long syndicId);

    // Batch : solde (paidAmount - totalDue) de PLUSIEURS copropriétaires en une seule requête, pour
    // la liste des copropriétaires (évite le N+1 d'une requête par ligne de page) — chaque ligne
    // retournée : [coOwnerId, solde]. Un copropriétaire sans aucun ChargeCallItem n'apparaît pas dans
    // le résultat (solde à traiter comme 0 côté appelant).
    @Query("SELECT cci.coOwner.id, COALESCE(SUM(cci.paidAmount), 0) - COALESCE(SUM(cci.quotePart + COALESCE(cci.penaltyAmount, 0)), 0) " +
           "FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE cci.coOwner.id IN :coOwnerIds " +
           "AND b.residence.syndic.id = :syndicId " +
           "GROUP BY cci.coOwner.id")
    List<Object[]> calculateSoldesByCoOwnerIdsAndSyndic(
            @Param("coOwnerIds") List<Long> coOwnerIds, @Param("syndicId") Long syndicId);

    // ===== CALCULS POUR DÉTAIL COPROPRIÉTAIRE (KPIs) =====

    /**
     * Card "Montant dû actuellement" (fiche détail copropriétaire) — une seule ligne : [currentAmountDue, currentPenaltyAmount]
     * currentAmountDue = SUM(totalDue - paidAmount) sur TOUTES les charges non soldées (status != PAID),
     * toutes années/résidences chez ce syndic — inclut les charges pas encore échues (pas seulement en retard)
     * currentPenaltyAmount = part de currentAmountDue venant uniquement des pénalités déjà appliquées
     * Remplace calculateSoldeByCoOwnerAndSyndic/sumPaymentsMadeByCoOwnerAndSyndic/sumUnpaidAmountByCoOwnerAndSyndic
     * (doublon Solde/Impayés supprimé — un seul KPI de dette)
     */
    @Query("SELECT COALESCE(SUM(cci.quotePart + COALESCE(cci.penaltyAmount, 0) - cci.paidAmount), 0), " +
           "       COALESCE(SUM(COALESCE(cci.penaltyAmount, 0)), 0) " +
           "FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE cci.coOwner.id = :coOwnerId " +
           "AND b.residence.syndic.id = :syndicId " +
           "AND cci.status != 'PAID'")
    List<Object[]> sumCurrentAmountDueByCoOwnerAndSyndic(@Param("coOwnerId") Long coOwnerId, @Param("syndicId") Long syndicId);

    // ===== CALCULS PAR RÉSIDENCE POUR FINANCES COPROPRIÉTAIRE =====

    /**
     * Card "Montant restant" (onglet Finances, fiche copropriétaire) — une seule ligne :
     * [remainingAmount, remainingPenaltyAmount], restreint à l'année en cours pour cette résidence.
     * remainingAmount = SUM(totalDue - paidAmount) sur les charges non soldées de l'année (pénalité
     * incluse), remainingPenaltyAmount = part venant uniquement de la pénalité.
     */
    @Query("SELECT COALESCE(SUM(cci.quotePart + COALESCE(cci.penaltyAmount, 0) - cci.paidAmount), 0), " +
           "       COALESCE(SUM(COALESCE(cci.penaltyAmount, 0)), 0) " +
           "FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "WHERE cci.coOwner.id = :coOwnerId " +
           "AND cc.budget.residence.id = :residenceId " +
           "AND cc.year = :year " +
           "AND cci.status != 'PAID'")
    List<Object[]> sumRemainingAmountByCoOwnerAndResidenceAndYear(
            @Param("coOwnerId") Long coOwnerId, @Param("residenceId") Long residenceId, @Param("year") Integer year);

    /**
     * Card "Taux de règlement" (onglet Finances, fiche copropriétaire) — une seule ligne :
     * [totalCallsCount, paidCallsCount], appels de charges émis cette année pour cette résidence où
     * il y avait réellement quelque chose à payer (NO_AMOUNT_DUE exclu — une quote-part à 0 ne doit
     * jamais faire baisser le taux de règlement), uniquement PAID pour le second.
     * ATTENTION : totalCallsCount n'est PAS fixe sur l'année — il grandit à chaque nouvel appel de
     * charges généré (T1 puis T2 puis T3...). Le taux reflète donc ce qui a déjà été appelé jusqu'à
     * maintenant, jamais une projection sur l'année complète.
     */
    @Query("SELECT COUNT(cci), SUM(CASE WHEN cci.status = 'PAID' THEN 1 ELSE 0 END) " +
           "FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "WHERE cci.coOwner.id = :coOwnerId " +
           "AND cc.budget.residence.id = :residenceId " +
           "AND cc.year = :year " +
           "AND cci.status != 'NO_AMOUNT_DUE'")
    List<Object[]> countCallsByCoOwnerAndResidenceAndYear(
            @Param("coOwnerId") Long coOwnerId, @Param("residenceId") Long residenceId, @Param("year") Integer year);

    /**
     * Somme des quote-parts générées pour un copropriétaire, une résidence et une année
     */
    @Query("SELECT COALESCE(SUM(cci.quotePart), 0) " +
           "FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE cci.coOwner.id = :coOwnerId " +
           "AND b.residence.id = :residenceId " +
           "AND cc.year = :year")
    BigDecimal sumQuotePartGeneratedByCoOwnerAndResidenceAndYear(@Param("coOwnerId") Long coOwnerId, @Param("residenceId") Long residenceId, @Param("year") Integer year);

    // ===== MÉTHODES SUPPLÉMENTAIRES =====

    /**
     * Tous les items d'une résidence, toutes périodes confondues
     */
    @Query("SELECT cci FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE b.residence.id = :residenceId")
    List<ChargeCallItem> findByChargeCallBudgetResidenceId(@Param("residenceId") Long residenceId);

    /**
     * Items d'une résidence créés dans une période précise (via la date de création du ChargeCall parent)
     */
    @Query("SELECT cci FROM ChargeCallItem cci " +
           "JOIN cci.chargeCall cc " +
           "JOIN cc.budget b " +
           "WHERE b.residence.id = :residenceId " +
           "AND cc.createdAt BETWEEN :start AND :end")
    List<ChargeCallItem> findByChargeCallBudgetResidenceIdAndChargeCallCreatedAtBetween(
            @Param("residenceId") Long residenceId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * Items du syndic dont le solde impayé dépasse un seuil donné
     */
    @Query("SELECT i FROM ChargeCallItem i WHERE i.chargeCall.budget.syndic.id = :syndicId " +
           "AND (i.quotePart - i.paidAmount) > :threshold")
    List<ChargeCallItem> findByChargeCallBudgetSyndicIdAndRemainingAmountGreaterThan(
            @Param("syndicId") Long syndicId, @Param("threshold") BigDecimal threshold);

    // Récupère le nombre de jours de retard le plus important parmi tous les ChargeCallItem non soldés
    // de ce copropriétaire, restreint aux résidences de ce syndic. Retourne null si aucun item en retard.
    // Statut PENDING uniquement désormais : PARTIALLY_PAID supprimé, aucun paiement partiel autorisé.
    @Query("SELECT MAX(DATEDIFF(CURRENT_DATE, i.chargeCall.dueDate)) FROM ChargeCallItem i " +
            "WHERE i.coOwner.id = :coOwnerId " +
            "AND i.chargeCall.budget.syndic.id = :syndicId " +
            "AND i.status = 'PENDING' " +
            "AND i.chargeCall.dueDate < CURRENT_DATE")
    Integer findMaxDaysLateByCoOwnerAndSyndic(@Param("coOwnerId") Long coOwnerId, @Param("syndicId") Long syndicId);

    // ===== PAIEMENTS / IMPAYÉS (GLOBAL SYNDIC) =====

    // Items déjà soldés (PAID) d'un syndic, filtrés optionnellement par résidence, année et
    // recherche nom copropriétaire, paginés — pour l'onglet Paiements (module Charges)
    @Query("SELECT i FROM ChargeCallItem i WHERE i.chargeCall.budget.syndic.id = :syndicId " +
           "AND i.status = 'PAID' " +
           "AND (:residenceId IS NULL OR i.chargeCall.budget.residence.id = :residenceId) " +
           "AND (:year IS NULL OR i.chargeCall.year = :year) " +
           "AND (:search IS NULL OR :search = '' " +
           "     OR LOWER(i.coOwner.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(i.coOwner.lastName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<ChargeCallItem> findPaidBySyndicIdWithFilters(@Param("syndicId") Long syndicId,
                                                        @Param("residenceId") Long residenceId,
                                                        @Param("year") Integer year,
                                                        @Param("search") String search,
                                                        Pageable pageable);

    // Items non soldés d'un syndic, paginés (pour l'onglet Impayés) — basé sur le statut posé
    // explicitement au paiement. PARTIALLY_PAID supprimé : un item non soldé est toujours PENDING.
    @Query("SELECT i FROM ChargeCallItem i WHERE i.chargeCall.budget.syndic.id = :syndicId AND i.status = 'PENDING'")
    Page<ChargeCallItem> findUnpaidByBudgetSyndicId(@Param("syndicId") Long syndicId, Pageable pageable);

    // Tous les items non soldés, sans pagination (pour calculer les KPI globaux : total, count)
    @Query("SELECT i FROM ChargeCallItem i WHERE i.chargeCall.budget.syndic.id = :syndicId AND i.status = 'PENDING'")
    List<ChargeCallItem> findAllUnpaidByBudgetSyndicId(@Param("syndicId") Long syndicId);

    // Variantes filtrées (résidence + année optionnelles) des deux méthodes ci-dessus, utilisées
    // par l'onglet Impayés du module Charges (/api/syndic/budget/unpaid)
    @Query("SELECT i FROM ChargeCallItem i WHERE i.chargeCall.budget.syndic.id = :syndicId AND i.status = 'PENDING' " +
           "AND (:residenceId IS NULL OR i.chargeCall.budget.residence.id = :residenceId) " +
           "AND (:year IS NULL OR i.chargeCall.year = :year)")
    Page<ChargeCallItem> findUnpaidBySyndicIdWithFilters(@Param("syndicId") Long syndicId,
                                                          @Param("residenceId") Long residenceId,
                                                          @Param("year") Integer year,
                                                          Pageable pageable);

    @Query("SELECT i FROM ChargeCallItem i WHERE i.chargeCall.budget.syndic.id = :syndicId AND i.status = 'PENDING' " +
           "AND (:residenceId IS NULL OR i.chargeCall.budget.residence.id = :residenceId) " +
           "AND (:year IS NULL OR i.chargeCall.year = :year)")
    List<ChargeCallItem> findAllUnpaidBySyndicIdWithFilters(@Param("syndicId") Long syndicId,
                                                             @Param("residenceId") Long residenceId,
                                                             @Param("year") Integer year);

    // Tous les items d'un syndic (non paginé), toutes résidences confondues (pour KPI dashboard global)
    @Query("SELECT i FROM ChargeCallItem i WHERE i.chargeCall.budget.syndic.id = :syndicId")
    List<ChargeCallItem> findAllByBudgetSyndicId(@Param("syndicId") Long syndicId);

    // Items d'un syndic créés dans une période précise (via la date de création du ChargeCall parent),
    // toutes résidences confondues (pour les évolutions globales du dashboard)
    @Query("SELECT i FROM ChargeCallItem i WHERE i.chargeCall.budget.syndic.id = :syndicId " +
           "AND i.chargeCall.createdAt BETWEEN :start AND :end")
    List<ChargeCallItem> findByChargeCallBudgetSyndicIdAndChargeCallCreatedAtBetween(
            @Param("syndicId") Long syndicId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // Compte les lignes en retard (date d'échéance dépassée) et non soldées pour un syndic (toutes résidences)
    @Query("SELECT COUNT(i) FROM ChargeCallItem i " +
           "WHERE i.chargeCall.budget.syndic.id = :syndicId " +
           "AND i.status = 'PENDING' " +
           "AND i.chargeCall.dueDate < CURRENT_DATE")
    long countLateUnpaidBySyndicId(@Param("syndicId") Long syndicId);

    // Compte les lignes en retard (date d'échéance dépassée) et non soldées pour une résidence précise —
    // utilisé pour l'alerte "Paiements en retard" sur la carte résidence (admin)
    @Query("SELECT COUNT(i) FROM ChargeCallItem i " +
           "JOIN i.chargeCall cc JOIN cc.budget b " +
           "WHERE b.residence.id = :residenceId " +
           "AND i.status = 'PENDING' " +
           "AND cc.dueDate < CURRENT_DATE")
    long countLateUnpaidByResidenceId(@Param("residenceId") Long residenceId);

    // Additionne tout ce qui reste à payer pour ce copropriétaire, dans cette résidence,
    // toutes périodes de charge confondues (peu importe le statut de chaque ChargeCall) — le montant
    // restant se calcule (quotePart - paidAmount), mais seuls les items encore PENDING sont inclus
    @Query("SELECT COALESCE(SUM(item.quotePart - item.paidAmount), 0) FROM ChargeCallItem item " +
           "WHERE item.coOwner.id = :coOwnerId " +
           "AND item.chargeCall.budget.residence.id = :residenceId " +
           "AND item.status = 'PENDING'")
    BigDecimal sumRemainingAmountByCoOwnerAndResidence(@Param("coOwnerId") Long coOwnerId,
                                                      @Param("residenceId") Long residenceId);

    // Charges en attente de ce copropriétaire pour une résidence précise,
    // triées par échéance la plus proche en premier
    @Query("SELECT item FROM ChargeCallItem item " +
           "WHERE item.coOwner.id = :coOwnerId " +
           "AND item.chargeCall.budget.residence.id = :residenceId " +
           "AND item.status = 'PENDING' " +
           "ORDER BY item.chargeCall.dueDate ASC")
    List<ChargeCallItem> findPendingItemsByCoOwnerAndResidence(@Param("coOwnerId") Long coOwnerId,
                                                              @Param("residenceId") Long residenceId,
                                                              Pageable pageable);

    // Supprimer tous les items d'un appel de charges
    void deleteByChargeCallId(Long chargeCallId);

    // ===== RELANCE AUTOMATIQUE (timeline "Option C") =====

    // Tous les items non soldés, toutes résidences et tous syndics confondus —
    // pour le job planifié quotidien (pas de "current user" dans un job planifié)
    @Query("SELECT i FROM ChargeCallItem i WHERE i.status = 'PENDING'")
    List<ChargeCallItem> findAllUnpaidItems();

    // Compte les lignes en retard (1 à 30 jours après échéance) et non soldées pour un syndic —
    // seuil identique à PaymentStatusUtils.UNPAID_THRESHOLD_DAYS (digest quotidien du syndic)
    @Query("SELECT COUNT(i) FROM ChargeCallItem i " +
           "WHERE i.chargeCall.budget.syndic.id = :syndicId " +
           "AND i.status = 'PENDING' " +
           "AND DATEDIFF(CURRENT_DATE, i.chargeCall.dueDate) BETWEEN 1 AND 30")
    long countLateBySyndicId(@Param("syndicId") Long syndicId);

    // Compte les lignes impayées (plus de 30 jours après échéance) et non soldées pour un syndic —
    // seuil identique à PaymentStatusUtils.UNPAID_THRESHOLD_DAYS (digest quotidien du syndic)
    @Query("SELECT COUNT(i) FROM ChargeCallItem i " +
           "WHERE i.chargeCall.budget.syndic.id = :syndicId " +
           "AND i.status = 'PENDING' " +
           "AND DATEDIFF(CURRENT_DATE, i.chargeCall.dueDate) > 30")
    long countUnpaidBySyndicId(@Param("syndicId") Long syndicId);

    // countPartiallyPaidBySyndicId supprimée — PARTIALLY_PAID n'existe plus, plus aucun paiement
    // partiel n'est accepté (voir SolimusCallbackController)

    // ============================================================
    // "MES CHARGES" (copropriétaire) — UNION ALL ChargeCallItem + ExceptionalCallItem, paginée en base
    // ============================================================
    // Le libellé de période ("Charges T3 2026", "Charges Août 2026") n'est PAS recalculé ici pour
    // l'affichage — seulement dupliqué dans search_title pour permettre au filtre "search" de matcher
    // les charges courantes (dont le titre affiché est calculé en Java, voir
    // OwnerChargeServiceImpl.buildPeriodLabel). L'affichage final reste construit en Java, uniquement
    // sur les lignes de la page déjà récupérée — une seule source de vérité pour le libellé affiché.

    String MY_CHARGES_CHARGE_BRANCH =
            "SELECT 'CHARGE' AS source_type, " +
            "cci.id AS id, " +
            "NULL AS title, " +
            "'REGULAR' AS type_code, " +
            "r.name AS residence_name, " +
            "r.id AS residence_id, " +
            "(SELECT GROUP_CONCAT(p.reference SEPARATOR ', ') FROM properties p " +
            "   WHERE p.owner_id = cci.coowner_id AND p.residence_id = r.id) AS property_reference, " +
            "(cci.quote_part + COALESCE(cci.penalty_amount, 0) - COALESCE(cci.paid_amount, 0)) AS remaining_amount, " +
            "cc.due_date AS due_date, " +
            "cci.status AS status_raw, " +
            "(b.status = 'CLOSED') AS payment_blocked, " +
            "cc.frequency AS frequency, " +
            "cc.period_number AS period_number, " +
            "cc.annee AS year, " +
            "CONCAT('Charges ', " +
            "  CASE WHEN cc.frequency = 'MENSUEL' THEN " +
            "    ELT(cc.period_number, 'Janvier','Février','Mars','Avril','Mai','Juin','Juillet','Août','Septembre','Octobre','Novembre','Décembre') " +
            "  ELSE " +
            "    CONCAT('T', cc.period_number, ' (', ELT(cc.period_number, 'Jan-Mar','Avr-Jun','Jul-Sep','Oct-Dec'), ')') " +
            "  END, ' ', cc.annee) AS search_title " +
            "FROM charge_call_items cci " +
            "JOIN charge_calls cc ON cc.id = cci.charge_call_id " +
            "JOIN budgets b ON b.id = cc.budget_id " +
            "JOIN residences r ON r.id = b.residence_id " +
            "WHERE cci.coowner_id = :coOwnerId " +
            "AND (:residenceId IS NULL OR r.id = :residenceId) " +
            "AND (:type IS NULL OR :type = 'REGULAR') " +
            "AND (:statusRaw IS NULL OR cci.status = :statusRaw) ";

    String MY_CHARGES_EXCEPTIONAL_BRANCH =
            "SELECT 'EXCEPTIONAL' AS source_type, " +
            "eci.id AS id, " +
            "ec.title AS title, " +
            "'EXCEPTIONAL' AS type_code, " +
            "r2.name AS residence_name, " +
            "r2.id AS residence_id, " +
            "(SELECT GROUP_CONCAT(p2.reference SEPARATOR ', ') FROM properties p2 " +
            "   WHERE p2.owner_id = eci.co_owner_id AND p2.residence_id = r2.id) AS property_reference, " +
            "(eci.quote_part - COALESCE(eci.paid_amount, 0)) AS remaining_amount, " +
            "NULL AS due_date, " +
            "eci.status AS status_raw, " +
            "(ec.status = 'CLOSED') AS payment_blocked, " +
            "NULL AS frequency, " +
            "NULL AS period_number, " +
            "NULL AS year, " +
            "ec.title AS search_title " +
            "FROM exceptional_call_items eci " +
            "JOIN exceptional_calls ec ON ec.id = eci.exceptional_call_id " +
            "JOIN residences r2 ON r2.id = ec.residence_id " +
            "WHERE eci.co_owner_id = :coOwnerId " +
            "AND (:residenceId IS NULL OR r2.id = :residenceId) " +
            "AND (:type IS NULL OR :type = 'EXCEPTIONAL') " +
            "AND (:statusRaw IS NULL OR eci.status = :statusRaw) ";

    // Page de "mes charges" (courantes + exceptionnelles fusionnées), triée par échéance croissante
    // (NULL en dernier, comme Comparator.nullsLast), LIMIT/OFFSET géré par la base
    @Query(value =
            "SELECT * FROM (" + MY_CHARGES_CHARGE_BRANCH + " UNION ALL " + MY_CHARGES_EXCEPTIONAL_BRANCH + ") AS combined " +
            "WHERE (:search IS NULL OR :search = '' OR LOWER(search_title) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "ORDER BY due_date IS NULL, due_date ASC",
            countQuery =
            "SELECT COUNT(*) FROM (" + MY_CHARGES_CHARGE_BRANCH + " UNION ALL " + MY_CHARGES_EXCEPTIONAL_BRANCH + ") AS combined " +
            "WHERE (:search IS NULL OR :search = '' OR LOWER(search_title) LIKE LOWER(CONCAT('%', :search, '%')))",
            nativeQuery = true)
    Page<Object[]> findMyChargesUnion(@Param("coOwnerId") Long coOwnerId,
                                       @Param("residenceId") Long residenceId,
                                       @Param("type") String type,
                                       @Param("statusRaw") String statusRaw,
                                       @Param("search") String search,
                                       Pageable pageable);

    // Résumé (bandeau haut "Mes charges") calculé sur TOUT l'ensemble filtré, pas seulement la page —
    // une seule ligne : [totalToPay, pendingCount, nextDueDate]
    @Query(value =
            "SELECT COALESCE(SUM(CASE WHEN remaining_amount > 0 THEN remaining_amount ELSE 0 END), 0) AS total_to_pay, " +
            "       COALESCE(SUM(CASE WHEN remaining_amount > 0 THEN 1 ELSE 0 END), 0) AS pending_count, " +
            "       MIN(CASE WHEN remaining_amount > 0 THEN due_date ELSE NULL END) AS next_due_date " +
            "FROM (" + MY_CHARGES_CHARGE_BRANCH + " UNION ALL " + MY_CHARGES_EXCEPTIONAL_BRANCH + ") AS combined " +
            "WHERE (:search IS NULL OR :search = '' OR LOWER(search_title) LIKE LOWER(CONCAT('%', :search, '%')))",
            nativeQuery = true)
    List<Object[]> sumMyChargesSummary(@Param("coOwnerId") Long coOwnerId,
                                        @Param("residenceId") Long residenceId,
                                        @Param("type") String type,
                                        @Param("statusRaw") String statusRaw,
                                        @Param("search") String search);
}
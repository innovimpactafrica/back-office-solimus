package com.example.solimus.services.syndic.dashboard;

import com.example.solimus.dtos.syndic.dashboard.*;
import com.example.solimus.dtos.syndic.travaux.SyndicResidenceDTO;
import com.example.solimus.entities.*;
import com.example.solimus.enums.*;
import com.example.solimus.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DasboardServiceImpl implements DashboardService {

    private final UserRepository userRepository;
    private final ResidenceRepository residenceRepository;
    private final PropertyRepository propertyRepository;
    private final SyndicWalletRepository syndicWalletRepository;
    private final SyndicWalletTransactionRepository syndicWalletTransactionRepository;
    private final SyndicWithdrawalRequestRepository syndicWithdrawalRequestRepository;
    private final ChargeCallRepository chargeCallRepository;
    private final ChargeCallItemRepository chargeCallItemRepository;
    private final InterventionRequestRepository interventionRequestRepository;
    private final MeetingRepository meetingRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ExceptionalCallRepository exceptionalCallRepository;

    // =========================================================================
    // TABLEAU DE BORD PRINCIPAL (KPIs, résidence optionnelle avec repli automatique)
    // =========================================================================

    @Override
    public MainDashboardDTO getMainDashboard(Long residenceId) {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Résout la résidence à utiliser : celle fournie, ou null pour calculs globaux
        Long resolvedResidenceId = (residenceId != null) ? resolveResidenceId(residenceId, currentSyndic) : null;

        // Récupère le wallet du syndic (peut être null si aucun wallet n'a encore été créé)
        SyndicWallet wallet = syndicWalletRepository.findBySyndicId(currentSyndic.getId()).orElse(null);
        Long walletId = (wallet != null) ? wallet.getId() : null;

        // Crée le DTO de réponse vide
        MainDashboardDTO dto = new MainDashboardDTO();

        // --- Trésorerie Totale (globale ou filtrée par résidence) ---

        BigDecimal treasuryBrute;
        BigDecimal retraitsReserves;

        if (resolvedResidenceId != null) {
            // Mode filtré par résidence
            treasuryBrute = syndicWalletTransactionRepository.sumAllByResidenceId(resolvedResidenceId, LocalDateTime.now());
            retraitsReserves = (walletId != null)
                    ? syndicWithdrawalRequestRepository.sumPendingAndValidatedByWalletAndResidence(walletId, resolvedResidenceId)
                    : BigDecimal.ZERO;
        } else {
            // Mode global (toutes résidences)
            treasuryBrute = calculerSoldeADate(walletId, LocalDateTime.now());
            retraitsReserves = (walletId != null)
                    ? syndicWithdrawalRequestRepository.sumPendingAndValidatedByWalletAndResidence(walletId, null)
                    : BigDecimal.ZERO;
        }

        // Trésorerie disponible = transactions - retraits réservés
        BigDecimal treasuryDisponible = treasuryBrute.subtract(retraitsReserves);
        dto.setTreasuryTotal(treasuryDisponible);

        // Calcule la date de fin du mois précédent (= début du mois actuel)
        LocalDateTime finMoisPrecedent = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        // Calcule le solde qu'il y avait à cette date-là (global ou filtré)
        BigDecimal treasuryMoisPrecedent;
        if (resolvedResidenceId != null) {
            treasuryMoisPrecedent = syndicWalletTransactionRepository.sumAllByResidenceId(resolvedResidenceId, finMoisPrecedent);
        } else {
            treasuryMoisPrecedent = calculerSoldeADate(walletId, finMoisPrecedent);
        }

        // Calcule la variation en pourcentage (uniquement sur les flux de transactions, sans les retraits réservés)
        dto.setTreasuryEvolutionPercent(calculerVariation(treasuryBrute, treasuryMoisPrecedent).doubleValue());

        // --- Taux de Recouvrement + Impayés (globaux ou filtrés par résidence) ---

        List<ChargeCallItem> allItems;
        if (resolvedResidenceId != null) {
            allItems = chargeCallItemRepository.findByChargeCallBudgetResidenceId(resolvedResidenceId);
        } else {
            allItems = chargeCallItemRepository.findAllByBudgetSyndicId(currentSyndic.getId());
        }

        // Additionne tous les montants dus
        BigDecimal totalDue = allItems.stream().map(ChargeCallItem::getQuotePart).reduce(BigDecimal.ZERO, BigDecimal::add);
        // Additionne tous les montants déjà payés
        BigDecimal totalPaid = allItems.stream().map(ChargeCallItem::getPaidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        // Additionne tous les montants restants à payer
        BigDecimal totalUnpaid = allItems.stream().map(item -> item.getQuotePart().subtract(item.getPaidAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calcule le taux de recouvrement (protection contre la division par zéro)
        double recoveryRate = 0.0;
        if (totalDue.compareTo(BigDecimal.ZERO) > 0) {
            recoveryRate = totalPaid.divide(totalDue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();
        }
        dto.setRecoveryRate(recoveryRate);
        dto.setUnpaidAmount(totalUnpaid);

        // Calcule les évolutions vs le mois dernier (comparaison mois complet vs mois complet précédent)
        dto.setRecoveryRateEvolutionPercent(calculateRecoveryRateEvolution(resolvedResidenceId, currentSyndic.getId()));
        dto.setUnpaidEvolutionPercent(calculateUnpaidEvolution(resolvedResidenceId, currentSyndic.getId()));

        // --- Résidences Gérées (TOUJOURS global syndic, indépendant de la résidence sélectionnée) ---

        // Récupère TOUTES les résidences du syndic, pas juste celle sélectionnée
        List<Residence> allResidences = residenceRepository.findBySyndicId(currentSyndic.getId());
        dto.setManagedResidencesCount(allResidences.size());

        // Compte le nombre total de lots, toutes résidences du syndic confondues
        int totalLots = allResidences.stream()
                .mapToInt(r -> propertyRepository.findByResidenceId(r.getId()).size())
                .sum();
        dto.setTotalLotsCount(totalLots);

        // --- Incidents Ouverts (globaux ou filtrés par résidence) ---

        // Liste des statuts considérés comme "ouverts" (tout sauf clôturé ou annulé)
        List<InterventionStatus> openStatuses = List.of(
                InterventionStatus.PENDING, InterventionStatus.SYNDIC_ASSIGNED,
                InterventionStatus.QUOTE_VALIDATED, InterventionStatus.STARTED, InterventionStatus.FINISHED
        );

        long openIncidentsCount;
        long urgentIncidentsCount;

        if (resolvedResidenceId != null) {
            // Mode filtré par résidence
            openIncidentsCount = interventionRequestRepository.countByResidenceIdAndStatusIn(resolvedResidenceId, openStatuses);
            urgentIncidentsCount = interventionRequestRepository
                    .countByResidenceIdAndStatusInAndUrgencyLevel(resolvedResidenceId, openStatuses, UrgencyLevel.URGENT);
        } else {
            // Mode global (toutes résidences)
            openIncidentsCount = interventionRequestRepository.countByResidenceSyndicIdAndStatusIn(currentSyndic.getId(), openStatuses);
            urgentIncidentsCount = interventionRequestRepository
                    .countByResidenceSyndicIdAndStatusInAndUrgencyLevel(currentSyndic.getId(), openStatuses, UrgencyLevel.URGENT);
        }

        dto.setOpenIncidentsCount(openIncidentsCount);
        dto.setUrgentIncidentsCount(urgentIncidentsCount);

        // --- Interventions du Jour (globales ou filtrées par résidence) ---

        // Calcule les bornes de la journée actuelle (00h00 à 23h59)
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime endOfToday = startOfToday.plusDays(1);

        long todayInterventionsCount;
        long plannedInterventionsCount;

        if (resolvedResidenceId != null) {
            // Mode filtré par résidence
            todayInterventionsCount = interventionRequestRepository
                    .countByResidenceIdAndCreatedAtBetween(resolvedResidenceId, startOfToday, endOfToday);
            plannedInterventionsCount = interventionRequestRepository
                    .countByResidenceIdAndCreatedAtBetweenAndStatus(resolvedResidenceId, startOfToday, endOfToday, InterventionStatus.PENDING);
        } else {
            // Mode global (toutes résidences)
            todayInterventionsCount = interventionRequestRepository
                    .countByResidenceSyndicIdAndCreatedAtBetween(currentSyndic.getId(), startOfToday, endOfToday);
            plannedInterventionsCount = interventionRequestRepository
                    .countByResidenceSyndicIdAndCreatedAtBetweenAndStatus(currentSyndic.getId(), startOfToday, endOfToday, InterventionStatus.PENDING);
        }

        dto.setTodayInterventionsCount(todayInterventionsCount);
        dto.setPlannedInterventionsCount(plannedInterventionsCount);

        // Retourne le DTO complet avec toutes les 6 cards remplies
        return dto;
    }

    // =========================================================================
    // ALERTES IMPORTANTES (AG à venir + paiements en retard + intervention urgente)
    // Vue résumée, toutes résidences confondues (max 3 alertes)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<AlertDTO> getImportantAlerts() {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();
        // Liste qui va contenir toutes les alertes, tous types confondus
        List<AlertDTO> alerts = new ArrayList<>();

        // --- AG à venir (la plus proche) ---

        // Récupère toutes les réunions à venir (UPCOMING) pour ce syndic
        List<Meeting> upcomingMeetings = meetingRepository
                .findBySyndicIdAndStatus(currentSyndic.getId(), MeetingStatus.UPCOMING);

        // Sélectionne la réunion la plus proche (date de réunion la plus petite)
        Meeting nearestMeeting = upcomingMeetings.stream()
                .filter(m -> m.getMeetingDate() != null)
                .min((a, b) -> a.getMeetingDate().compareTo(b.getMeetingDate()))
                .orElse(null);

        if (nearestMeeting != null) {
            AlertDTO alert = new AlertDTO();
            alert.setType("MEETING");
            alert.setTitle("AG à venir");
            alert.setDescription(nearestMeeting.getResidence().getName() + " - "
                    + nearestMeeting.getMeetingDate().format(DateTimeFormatter.ofPattern("d MMMM yyyy")));
            alert.setOccurredAt(nearestMeeting.getCreatedAt());
            alerts.add(alert);
        }

        // --- Paiements en retard (alerte si plus de 10) ---

        // Compte les lignes en retard (échéance dépassée) et non soldées, toutes résidences
        long latePaymentsCount = chargeCallItemRepository.countLateUnpaidBySyndicId(currentSyndic.getId());

        if (latePaymentsCount > 10) {
            AlertDTO alert = new AlertDTO();
            alert.setType("UNPAID");
            alert.setTitle("Paiements en retard");
            alert.setDescription(latePaymentsCount + " paiements en retard");
            alert.setOccurredAt(LocalDateTime.now());
            alerts.add(alert);
        }

        // --- Intervention urgente (la dernière déclarée) ---

        // Récupère la dernière intervention urgente non résolue déclarée pour ce syndic
        List<InterventionRequest> urgentInterventions = interventionRequestRepository
                .findLatestUrgentBySyndicId(currentSyndic.getId(), UrgencyLevel.URGENT, PageRequest.of(0, 1));

        if (!urgentInterventions.isEmpty()) {
            InterventionRequest intervention = urgentInterventions.get(0);
            AlertDTO alert = new AlertDTO();
            alert.setType("INTERVENTION");
            alert.setTitle("Intervention urgente");
            alert.setDescription(intervention.getResidence().getName() + " - " + intervention.getTitle());
            alert.setOccurredAt(intervention.getCreatedAt());
            alerts.add(alert);
        }

        // Trie toutes les alertes par date décroissante, les plus récentes en premier
        alerts.sort((a, b) -> b.getOccurredAt().compareTo(a.getOccurredAt()));

        // Calcule le texte "il y a Xh" pour chaque alerte, une fois l'ordre final déterminé
        for (AlertDTO alert : alerts) {
            alert.setRelativeTime(buildRelativeTime(alert.getOccurredAt()));
        }

        // Retourne la liste complète des alertes triées
        return alerts;
    }

    // =========================================================================
    // ACTIVITÉS RÉCENTES (toutes résidences confondues)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<ActivityRowDTO> getRecentActivities(int limit) {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Récupère les dernières activités du syndic, toutes résidences confondues, limitées au nombre demandé
        List<ActivityLog> logs = activityLogRepository
                .findByResidenceSyndicIdOrderByCreatedAtDesc(currentSyndic.getId(), PageRequest.of(0, limit));

        // Transforme chaque activité en ligne de tableau
        return logs.stream()
                .map(this::buildActivityRow)
                .toList();
    }

    // =========================================================================
    // INCIDENTS RÉCENTS (toutes résidences confondues)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<RecentIncidentDTO> getRecentIncidents(int limit) {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Récupère les dernières interventions gérées par le syndic (pas les auto-gérées par les copropriétaires)
        List<InterventionRequest> interventions = interventionRequestRepository
                .findByResidenceSyndicIdAndManagementModeOrderByCreatedAtDesc(
                        currentSyndic.getId(), InterventionManagementMode.SYNDIC, PageRequest.of(0, limit));

        // Transforme chaque intervention en ligne de tableau
        return interventions.stream()
                .map(this::buildRecentIncidentDto)
                .toList();
    }

    // =========================================================================
    // LISTE DES RÉSIDENCES (pour peupler le dropdown de sélection)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<SyndicResidenceDTO> getMyResidencesForDropdown() {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Récupère toutes les résidences de ce syndic, les transforme en DTO simplifié (id + nom)
        return residenceRepository.findBySyndicId(currentSyndic.getId()).stream()
                .map(r -> SyndicResidenceDTO.builder().id(r.getId()).name(r.getName()).build())
                .toList();
    }

    // =========================================================================
    // UTILITAIRES ET MAPPERS
    // =========================================================================

    // Récupère l'utilisateur actuellement authentifié via le SecurityContext
    private User getCurrentUser() {
        // Récupère l'email stocké dans le token JWT de l'utilisateur connecté
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        // Recherche l'utilisateur correspondant à cet email
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }

    // Résout l'ID de résidence à utiliser : celui fourni (après vérification qu'il appartient au syndic),
    // ou automatiquement la résidence la plus récemment créée par ce syndic si aucun ID n'est fourni
    private Long resolveResidenceId(Long residenceId, User currentSyndic) {

        // Cas 1 : une résidence précise a été demandée
        if (residenceId != null) {
            // Récupère la résidence, erreur si elle n'existe pas
            Residence residence = residenceRepository.findById(residenceId)
                    .orElseThrow(() -> new RuntimeException("Résidence non trouvée"));

            // Sécurité : vérifie que cette résidence appartient bien au syndic connecté
            if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
                throw new RuntimeException("Vous n'êtes pas autorisé à accéder à cette résidence");
            }
            // Retourne l'ID de cette résidence
            return residence.getId();
        }

        // Cas 2 : aucune résidence précisée, on prend automatiquement la plus récemment créée
        return residenceRepository.findFirstBySyndicIdOrderByCreatedAtDesc(currentSyndic.getId())
                .map(Residence::getId)
                .orElseThrow(() -> new RuntimeException("Aucune résidence trouvée pour ce syndic"));
    }

    // Calcule le solde du wallet à une date donnée
    private BigDecimal calculerSoldeADate(Long walletId, LocalDateTime asOfDate) {
        // Si aucun wallet n'existe, le solde est considéré comme zéro
        if (walletId == null) return BigDecimal.ZERO;
        // Somme toutes les transactions du wallet jusqu'à cette date
        return syndicWalletTransactionRepository.sumTransactionsUpTo(walletId, asOfDate);
    }

    // Calcule la variation en pourcentage entre deux montants
    private BigDecimal calculerVariation(BigDecimal actuel, BigDecimal precedent) {
        // Évite une division par zéro si le montant précédent était nul
        if (precedent.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        // Calcule (actuel - précédent) / précédent * 100
        return actuel.subtract(precedent)
                .divide(precedent, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // Calcule l'évolution du taux de recouvrement entre le mois dernier complet et le mois d'avant
    private Double calculateRecoveryRateEvolution(Long residenceId, Long syndicId) {

        // Calcule les bornes de dates du mois dernier complet
        LocalDate now = LocalDate.now();
        LocalDate startLastMonth = now.withDayOfMonth(1).minusMonths(1);
        LocalDate endLastMonth = now.withDayOfMonth(1);
        // Calcule les bornes de dates du mois d'avant (encore plus tôt)
        LocalDate startMonthBefore = startLastMonth.minusMonths(1);

        // Calcule le taux de recouvrement pour chacune des deux périodes
        double lastMonthRate = getRecoveryRateForPeriod(residenceId, syndicId, startLastMonth, endLastMonth);
        double monthBeforeRate = getRecoveryRateForPeriod(residenceId, syndicId, startMonthBefore, startLastMonth);

        // Retourne la différence en points de pourcentage
        return lastMonthRate - monthBeforeRate;
    }

    // Calcule le taux de recouvrement sur une période donnée
    private double getRecoveryRateForPeriod(Long residenceId, Long syndicId, LocalDate start, LocalDate end) {
        // Récupère les items de la résidence ou du syndic créés dans cette période précise
        List<ChargeCallItem> items;
        if (residenceId != null) {
            items = chargeCallItemRepository
                    .findByChargeCallBudgetResidenceIdAndChargeCallCreatedAtBetween(
                            residenceId, start.atStartOfDay(), end.atStartOfDay());
        } else {
            items = chargeCallItemRepository
                    .findByChargeCallBudgetSyndicIdAndChargeCallCreatedAtBetween(
                            syndicId, start.atStartOfDay(), end.atStartOfDay());
        }

        // Additionne les montants dus et payés sur cette période
        BigDecimal due = items.stream().map(ChargeCallItem::getQuotePart).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = items.stream().map(ChargeCallItem::getPaidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Évite la division par zéro, sinon calcule le pourcentage payé
        if (due.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return paid.divide(due, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    // Calcule l'évolution du montant impayé entre le mois dernier complet et le mois d'avant
    private Double calculateUnpaidEvolution(Long residenceId, Long syndicId) {

        // Calcule les bornes de dates du mois dernier complet et du mois d'avant
        LocalDate now = LocalDate.now();
        LocalDate startLastMonth = now.withDayOfMonth(1).minusMonths(1);
        LocalDate endLastMonth = now.withDayOfMonth(1);
        LocalDate startMonthBefore = startLastMonth.minusMonths(1);

        // Calcule le montant impayé pour chacune des deux périodes
        BigDecimal lastMonthUnpaid = getUnpaidForPeriod(residenceId, syndicId, startLastMonth, endLastMonth);
        BigDecimal monthBeforeUnpaid = getUnpaidForPeriod(residenceId, syndicId, startMonthBefore, startLastMonth);

        // Retourne la variation en pourcentage entre les deux périodes
        return calculerVariation(lastMonthUnpaid, monthBeforeUnpaid).doubleValue();
    }

    // Calcule le montant impayé sur une période donnée
    private BigDecimal getUnpaidForPeriod(Long residenceId, Long syndicId, LocalDate start, LocalDate end) {
        // Récupère les items de la résidence ou du syndic créés dans cette période précise
        List<ChargeCallItem> items;
        if (residenceId != null) {
            items = chargeCallItemRepository
                    .findByChargeCallBudgetResidenceIdAndChargeCallCreatedAtBetween(
                            residenceId, start.atStartOfDay(), end.atStartOfDay());
        } else {
            items = chargeCallItemRepository
                    .findByChargeCallBudgetSyndicIdAndChargeCallCreatedAtBetween(
                            syndicId, start.atStartOfDay(), end.atStartOfDay());
        }

        // Additionne les soldes restants de tous ces items
        return items.stream().map(item -> item.getQuotePart().subtract(item.getPaidAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Construit une ligne du tableau "Activités Récentes"
    private ActivityRowDTO buildActivityRow(ActivityLog log) {

        ActivityRowDTO dto = new ActivityRowDTO();
        // Traduit le type technique en libellé affiché (ex: PAYMENT_RECEIVED → "Paiement")
        dto.setType(mapActivityTypeToLabel(log.getType()));
        dto.setDescription(log.getMessage());
        dto.setResidenceName(log.getResidence().getName());
        dto.setOccurredAt(log.getCreatedAt());
        // Calcule le texte relatif ("Il y a 2h")
        dto.setRelativeTime(buildRelativeTime(log.getCreatedAt()));
        // Va chercher le vrai statut de l'entité liée (intervention, appel de charges...)
        dto.setStatus(resolveActivityStatus(log));

        return dto;
    }

    // Traduit l'ActivityType en libellé de colonne "Type" affiché
    private String mapActivityTypeToLabel(ActivityType type) {
        return switch (type) {
            case PAYMENT_RECEIVED, CHARGE_CALL_GENERATED -> "Paiement";
            case INTERVENTION_REPORTED, INTERVENTION_RESOLVED -> "Incident";
            case PROVIDER_ASSIGNED -> "Prestataire";
            case MEETING_CREATED, MEETING_PUBLISHED, MEETING_DELETED -> "Réunion";
            case MEETING_DOCUMENT_ADDED, MEETING_DOCUMENT_DOWNLOADED, MEETING_DOCUMENT_VIEWED, MEETING_DOCUMENT_UPDATED -> "Document";
            case COMMENT_ADDED -> "Commentaire";
            case BUDGET_CREATED, BUDGET_CLOSED, BUDGET_DELETED -> "Budget";
            case EXCEPTIONAL_CALL_CREATED, EXCEPTIONAL_CALL_ACTIVATED, EXCEPTIONAL_CALL_CLOSED -> "Appel exceptionnel";
        };
    }

    // Résout le statut d'une activité en interrogeant l'entité liée (relatedEntityType + relatedEntityId)
    private String resolveActivityStatus(ActivityLog log) {

        // Si aucune entité n'est liée à cette activité, pas de statut à afficher
        if (log.getRelatedEntityType() == null || log.getRelatedEntityId() == null) {
            return null;
        }

        // Selon le type d'entité liée, va chercher le vrai statut à la bonne source
        switch (log.getRelatedEntityType()) {
            case "CHARGE_CALL":
                // Récupère l'appel de charges concerné, calcule son statut, retourne son label
                return chargeCallRepository.findById(log.getRelatedEntityId())
                        .map(cc -> calculateChargeCallStatus(cc).getLabel())
                        .orElse(null);

            case "EXCEPTIONAL_CALL":
                // Récupère l'appel exceptionnel concerné, retourne son statut brut sans transformation
                return exceptionalCallRepository.findById(log.getRelatedEntityId())
                        .map(ec -> ec.getStatus().getLabel())
                        .orElse(null);

            case "INTERVENTION":
                // Récupère l'intervention concernée, retourne son statut brut sans transformation
                return interventionRequestRepository.findById(log.getRelatedEntityId())
                        .map(i -> i.getStatus().getLabel())
                        .orElse(null);

            default:
                // Type pas encore géré — retourne null en attendant
                return null;
        }
    }

    // Calcule le statut de l'appel de charges à la volée : SETTLED, PARTIAL ou SENT (jamais stocké en base)
    private ChargeCallStatus calculateChargeCallStatus(ChargeCall chargeCall) {

        // Vérifie si TOUS les items ont payé au moins leur quote-part complète
        boolean allSettled = chargeCall.getItems().stream()
                .allMatch(item -> item.getPaidAmount().compareTo(item.getQuotePart()) >= 0);

        if (allSettled) {
            return ChargeCallStatus.SETTLED;
        }

        // Vérifie si AU MOINS UN item a reçu un paiement, même partiel
        boolean hasAtLeastOnePayment = chargeCall.getItems().stream()
                .anyMatch(item -> item.getPaidAmount().compareTo(BigDecimal.ZERO) > 0);

        if (hasAtLeastOnePayment) {
            return ChargeCallStatus.PARTIAL;
        }

        // Sinon, personne n'a encore payé
        return ChargeCallStatus.SENT;
    }


    // Construit une ligne du tableau "Incidents Récents"
    private RecentIncidentDTO buildRecentIncidentDto(InterventionRequest intervention) {

        RecentIncidentDTO dto = new RecentIncidentDTO();
        dto.setId(intervention.getId());
        dto.setTitle(intervention.getTitle());
        dto.setResidenceName(intervention.getResidence().getName());
        // Statut brut de l'intervention, tel qu'il est stocké — aucune déduction ni regroupement
        dto.setStatus(intervention.getStatus().getLabel());
        dto.setUrgencyLevel(intervention.getUrgencyLevel() != null ? intervention.getUrgencyLevel().name() : null);
        dto.setCreatedAt(intervention.getCreatedAt());

        return dto;
    }

    // Convertit une date en texte relatif ("Il y a 2h", "Hier", "Il y a 3 jours"...)
    private String buildRelativeTime(LocalDateTime date) {
        // Calcule la durée écoulée depuis cette date jusqu'à maintenant
        Duration duration = Duration.between(date, LocalDateTime.now());
        long hours = duration.toHours();

        // Choisit le format le plus adapté selon la durée écoulée
        if (hours < 1) return "Il y a " + duration.toMinutes() + " min";
        if (hours < 24) return "Il y a " + hours + "h";
        if (hours < 48) return "Hier";
        return "Il y a " + (hours / 24) + " jours";
    }
}
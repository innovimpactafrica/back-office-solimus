package com.example.solimus.services.syndic.dashboard;

import com.example.solimus.dtos.syndic.dashboard.*;
import com.example.solimus.dtos.syndic.travaux.SyndicResidenceDTO;
import com.example.solimus.entities.*;
import com.example.solimus.enums.*;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import com.example.solimus.services.shared.ActivityLogPresenter;
import com.example.solimus.services.shared.SyndicTreasuryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final ChargeCallRepository chargeCallRepository;
    private final ChargeCallItemRepository chargeCallItemRepository;
    private final InterventionRequestRepository interventionRequestRepository;
    private final MeetingRepository meetingRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ExceptionalCallRepository exceptionalCallRepository;
    private final SignalementRepository signalementRepository;
    private final ActivityLogPresenter activityLogPresenter;
    private final SyndicTreasuryService syndicTreasuryService;

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

        // Solde brut, utilisé plus bas pour l'évolution vs mois dernier (flux de transactions seuls)
        BigDecimal treasuryBrute = (resolvedResidenceId != null)
                ? syndicWalletTransactionRepository.sumAllByResidenceId(resolvedResidenceId, LocalDateTime.now())
                : calculerSoldeADate(walletId, LocalDateTime.now());

        // Trésorerie disponible = source unique (SyndicTreasuryService), ne soustrait que les retraits
        // réellement COMPLETED — jamais les PENDING (voir WithdrawalRequestServiceImpl pour le blocage
        // au moment de la validation, plus à la création de la demande)
        dto.setTreasuryTotal(syndicTreasuryService.getAvailableBalance(walletId, resolvedResidenceId));

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

        // Additionne tous les montants dus (quote-part + pénalité si déjà appliquée) — cohérent
        // avec les autres écrans financiers (Paiements, Impayés) qui utilisent aussi getTotalDue()
        BigDecimal totalDue = allItems.stream().map(item -> item.getTotalDue()).reduce(BigDecimal.ZERO, BigDecimal::add);
        // Additionne tous les montants déjà payés
        BigDecimal totalPaid = allItems.stream().map(ChargeCallItem::getPaidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        // Additionne tous les montants restants à payer
        BigDecimal totalUnpaid = allItems.stream().map(item -> item.getRemainingAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calcule le taux de recouvrement (protection contre la division par zéro)
        double recoveryRate = 0.0;
        if (totalDue.compareTo(BigDecimal.ZERO) > 0) {
            recoveryRate = totalPaid.divide(totalDue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();
        }
        dto.setRecoveryRate(recoveryRate);
        dto.setUnpaidAmount(totalUnpaid);

        // Évolution du taux de recouvrement : pas calculée pour l'instant, en attente de décider la
        // bonne formule (le calcul actuel — charges créées le mois dernier × paidAmount d'aujourd'hui —
        // donne un chiffre qui bouge rétroactivement, à revoir) // à définir
        dto.setRecoveryRateEvolutionPercent(null);
        // Même souci que recoveryRateEvolutionPercent ci-dessus (charges créées le mois dernier ×
        // remainingAmount d'aujourd'hui, chiffre pas stable dans le temps) — désactivée en attendant // à définir
        dto.setUnpaidEvolutionPercent(null);

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

        // --- Signalements Ouverts (globaux ou filtrés par résidence) ---

        long openSignalementsCount;
        long urgentSignalementsCount;

        if (resolvedResidenceId != null) {
            // Mode filtré par résidence
            openSignalementsCount = signalementRepository.countUnresolvedByResidenceId(resolvedResidenceId);
            urgentSignalementsCount = signalementRepository
                    .countUnresolvedByResidenceIdAndUrgencyLevel(resolvedResidenceId, UrgencyLevel.URGENT);
        } else {
            // Mode global (toutes résidences)
            openSignalementsCount = signalementRepository.countUnresolvedBySyndicId(currentSyndic.getId());
            urgentSignalementsCount = signalementRepository
                    .countUnresolvedBySyndicIdAndUrgencyLevel(currentSyndic.getId(), UrgencyLevel.URGENT);
        }

        dto.setOpenSignalementsCount(openSignalementsCount);
        dto.setUrgentSignalementsCount(urgentSignalementsCount);

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

        // --- Paiements en retard (alerte dès qu'il y en a au moins 1) ---

        // Compte les lignes en retard (échéance dépassée) et non soldées, toutes résidences
        long latePaymentsCount = chargeCallItemRepository.countLateUnpaidBySyndicId(currentSyndic.getId());

        if (latePaymentsCount > 0) {
            AlertDTO alert = new AlertDTO();
            alert.setType("UNPAID");
            alert.setTitle("Paiements en retard");
            alert.setDescription(latePaymentsCount + " paiements en retard");
            alert.setOccurredAt(LocalDateTime.now());
            alerts.add(alert);
        }

        // --- Travaux non résolus (nombre total, tous niveaux d'urgence confondus) ---

        // Même liste de statuts "ouverts" que le KPI "Incidents ouverts" du dashboard principal
        List<InterventionStatus> openStatuses = List.of(
                InterventionStatus.PENDING, InterventionStatus.SYNDIC_ASSIGNED,
                InterventionStatus.QUOTE_VALIDATED, InterventionStatus.STARTED, InterventionStatus.FINISHED
        );
        long openInterventionsCount = interventionRequestRepository
                .countByResidenceSyndicIdAndStatusIn(currentSyndic.getId(), openStatuses);

        if (openInterventionsCount > 0) {
            AlertDTO alert = new AlertDTO();
            alert.setType("INTERVENTION");
            alert.setTitle("Travaux non résolus");
            alert.setDescription(openInterventionsCount + " demande(s) de travaux non résolue(s)");
            alert.setOccurredAt(LocalDateTime.now());
            alerts.add(alert);
        }

        // --- Signalements en attente (nombre total, ni traités ni transformés en travaux) ---

        long pendingSignalementsCount = signalementRepository.countUnresolvedBySyndicId(currentSyndic.getId());

        if (pendingSignalementsCount > 0) {
            AlertDTO alert = new AlertDTO();
            alert.setType("SIGNALEMENT");
            alert.setTitle("Signalements en attente");
            alert.setDescription(pendingSignalementsCount + " signalement(s) en attente");
            alert.setOccurredAt(LocalDateTime.now());
            alerts.add(alert);
        }

        // Trie toutes les alertes par date décroissante, les plus récentes en premier
        alerts.sort((a, b) -> b.getOccurredAt().compareTo(a.getOccurredAt()));

        // Calcule le texte "il y a Xh" pour chaque alerte, une fois l'ordre final déterminé
        for (AlertDTO alert : alerts) {
            alert.setRelativeTime(activityLogPresenter.buildRelativeTime(alert.getOccurredAt()));
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
                .map(activityLogPresenter::buildActivityRow)
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
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }

    // Valide un residenceId fourni explicitement par l'appelant : vérifie qu'il existe et qu'il
    // appartient bien au syndic connecté. N'est jamais appelée avec un residenceId null — quand
    // aucune résidence n'est précisée, getMainDashboard reste volontairement en mode global
    // (toutes résidences confondues), il n'y a pas de repli automatique sur une résidence précise.
    private Long resolveResidenceId(Long residenceId, User currentSyndic) {

        // Récupère la résidence, erreur si elle n'existe pas
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Résidence non trouvée"));

        // Sécurité : vérifie que cette résidence appartient bien au syndic connecté
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à cette résidence");
        }
        // Retourne l'ID de cette résidence
        return residence.getId();
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
}
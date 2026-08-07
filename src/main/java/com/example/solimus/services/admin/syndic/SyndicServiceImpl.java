package com.example.solimus.services.admin.syndic;

import com.example.solimus.dtos.admin.syndic.*;
import com.example.solimus.dtos.syndic.dashboard.ActivityRowDTO;
import com.example.solimus.entities.ActivityLog;
import com.example.solimus.entities.Budget;
import com.example.solimus.entities.Residence;
import com.example.solimus.entities.Role;
import com.example.solimus.entities.Signalement;
import com.example.solimus.entities.SyndicPlan;
import com.example.solimus.entities.SyndicProfile;
import com.example.solimus.entities.SyndicSubscription;
import com.example.solimus.entities.User;
import com.example.solimus.enums.BudgetStatus;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.MeetingStatus;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.PropertyStatus;
import com.example.solimus.enums.SubscriptionDuration;
import com.example.solimus.enums.SubscriptionStatus;
import com.example.solimus.enums.UrgencyLevel;
import com.example.solimus.enums.UserStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.EmailAlreadyExistsException;
import com.example.solimus.exceptions.PhoneAlreadyExistsException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.ActivityLogRepository;
import com.example.solimus.repositories.BudgetRepository;
import com.example.solimus.repositories.ChargeCallItemRepository;
import com.example.solimus.repositories.InterventionRequestRepository;
import com.example.solimus.repositories.MeetingDocumentRepository;
import com.example.solimus.repositories.MeetingRepository;
import com.example.solimus.repositories.PropertyRepository;
import com.example.solimus.repositories.ResidenceRepository;
import com.example.solimus.repositories.RoleRepository;
import com.example.solimus.repositories.SignalementRepository;
import com.example.solimus.repositories.SyndicCoOwnerRelationRepository;
import com.example.solimus.repositories.SyndicPlanRepository;
import com.example.solimus.repositories.SyndicProfileRepository;
import com.example.solimus.repositories.SyndicSubscriptionRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.shared.ActivityLogPresenter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyndicServiceImpl implements SyndicService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SyndicPlanRepository syndicPlanRepository;
    private final SyndicProfileRepository syndicProfileRepository;
    private final SyndicSubscriptionRepository syndicSubscriptionRepository;
    private final ResidenceRepository residenceRepository;
    private final PropertyRepository propertyRepository;
    private final SyndicCoOwnerRelationRepository syndicCoOwnerRelationRepository;
    private final InterventionRequestRepository interventionRequestRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingDocumentRepository meetingDocumentRepository;
    private final BudgetRepository budgetRepository;
    private final SignalementRepository signalementRepository;
    private final ChargeCallItemRepository chargeCallItemRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ActivityLogPresenter activityLogPresenter;

    @Value("${app.touchpay.bridge-url}")
    private String touchPayBridgeUrlTemplate;

    // Statuts d'intervention considérés "en cours" (tout sauf validée définitivement ou annulée) —
    // même liste que celle utilisée par le tableau de bord syndic, pour rester cohérent partout
    private static final List<InterventionStatus> OPEN_INTERVENTION_STATUSES = List.of(
            InterventionStatus.PENDING, InterventionStatus.SYNDIC_ASSIGNED,
            InterventionStatus.QUOTE_VALIDATED, InterventionStatus.STARTED, InterventionStatus.FINISHED
    );

    // ============================================================================
    // BLOC — CRÉATION D'UN SYNDIC
    // ============================================================================

    @Override
    @Transactional
    public CreateSyndicResponseDTO createSyndic(CreateSyndicDTO dto) {

        // 1. Vérifie l'unicité de l'email et du téléphone
        if (userRepository.existsByEmail(dto.getEmail())) {
            // Un compte existe déjà avec cet email → on bloque avant de créer quoi que ce soit
            throw new EmailAlreadyExistsException("Un compte avec cet email existe déjà : " + dto.getEmail());
        }
        if (userRepository.existsByPhone(dto.getPhone())) {
            // Même vérification côté téléphone
            throw new PhoneAlreadyExistsException("Un compte avec ce téléphone existe déjà : " + dto.getPhone());
        }

        // 2. Récupère la formule d'abonnement choisie
        SyndicPlan plan = syndicPlanRepository.findById(dto.getSyndicPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Formule d'abonnement introuvable"));

        // 3. Détermine le montant selon la durée choisie (annuel ou mensuel)
        BigDecimal amountPaid = dto.getDuration() == SubscriptionDuration.YEARLY
                ? plan.getYearlyPrice()
                : plan.getMonthlyPrice();

        // Si l'admin n'a jamais configuré ce tarif pour la formule, on refuse plutôt que de facturer 0/null
        if (amountPaid == null) {
            throw new BadRequestException(
                    dto.getDuration() == SubscriptionDuration.YEARLY
                            ? "Cette formule ne propose pas de tarif annuel"
                            : "Cette formule ne propose pas de tarif mensuel");
        }

        // 4. Récupère le rôle SYNDIC
        Role syndicRole = roleRepository.findByName(ERole.ROLE_SYNDIC)
                .orElseThrow(() -> new ResourceNotFoundException("Rôle SYNDIC introuvable en base"));

        // 5. Crée le compte utilisateur — PENDING tant que le paiement n'est pas confirmé.
        // Le mot de passe reste null : tant que l'abonnement n'est pas payé, le syndic n'a reçu
        // aucun identifiant et n'a donc aucune raison de tenter de se connecter. Le mot de passe
        // réel (temporaire) n'est généré et posé qu'au moment du callback TouchPay confirmé.
        User user = new User();
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setCity(dto.getCity());
        user.setCountry(dto.getCountry().getLabel());
        user.setRole(syndicRole);
        user.setPassword(null);
        user.setStatus(UserStatus.PENDING);

        User savedUser = userRepository.save(user);

        // 6. Crée le profil société lié à ce syndic
        SyndicProfile profile = new SyndicProfile();
        profile.setUser(savedUser);
        profile.setCompanyName(dto.getCompanyName());
        profile.setAddress(dto.getAddress());
        // Coordonnées GPS optionnelles, remplies via l'autocomplétion d'adresse côté front
        profile.setLatitude(dto.getLatitude() != null ? BigDecimal.valueOf(dto.getLatitude()) : null);
        profile.setLongitude(dto.getLongitude() != null ? BigDecimal.valueOf(dto.getLongitude()) : null);
        syndicProfileRepository.save(profile);

        // 7. Crée l'abonnement en PENDING — il ne devient ACTIVE qu'à la confirmation réelle du paiement
        // (callback TouchPay), jamais directement ici
        LocalDateTime startDate = dto.getStartDate() != null
                ? dto.getStartDate().atStartOfDay()
                : LocalDateTime.now();
        LocalDateTime endDate = startDate.plusMonths(dto.getDuration().getMonths());

        // Référence unique préfixée SYN- pour que le bridge et le callback la reconnaissent
        String transactionRef = generateReference("SYN");

        // L'admin connecté est celui qui va réellement effectuer le paiement TouchPay
        // (le syndic n'existe pas encore fonctionnellement tant que le paiement n'est pas confirmé)
        User currentAdmin = getCurrentUser();

        SyndicSubscription subscription = new SyndicSubscription();
        subscription.setSyndic(savedUser);
        subscription.setInitiatedBy(currentAdmin);
        subscription.setSyndicPlan(plan);
        subscription.setStatus(SubscriptionStatus.PENDING);
        subscription.setPaymentStatus(PaymentStatus.PENDING);
        subscription.setAmountPaid(amountPaid);
        subscription.setMethod(dto.getMethod());
        subscription.setDuration(dto.getDuration());
        subscription.setTransactionRef(transactionRef);
        subscription.setStartDate(startDate);
        subscription.setEndDate(endDate);
        syndicSubscriptionRepository.save(subscription);

        // 8. Construit l'URL du pont de paiement TouchPay pour cette référence
        String bridgeUrl = String.format(touchPayBridgeUrlTemplate, transactionRef);

        log.info("Création syndic initiée (en attente de paiement) : {} ({}) — ref: {}",
                savedUser.getEmail(), dto.getCompanyName(), transactionRef);

        return CreateSyndicResponseDTO.builder()
                .userId(savedUser.getId())
                .firstName(savedUser.getFirstName())
                .lastName(savedUser.getLastName())
                .email(savedUser.getEmail())
                .companyName(dto.getCompanyName())
                .planName(plan.getName())
                .amountPaid(amountPaid)
                .startDate(startDate)
                .endDate(endDate)
                .transactionReference(transactionRef)
                .paymentUrl(bridgeUrl)
                .message("Compte syndic en attente de paiement. Veuillez compléter le paiement via TouchPay pour l'activer.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SyndicPlanOptionDTO> listAvailablePlans() {

        // Seules les formules actives peuvent être proposées pour un nouveau syndic
        return syndicPlanRepository.findByActiveTrue().stream()
                .map(plan -> SyndicPlanOptionDTO.builder()
                        .id(plan.getId())
                        .name(plan.getName())
                        .build())
                .toList();
    }

    // ============================================================================
    // BLOC — DASHBOARD (KPIs "Gestion des syndics")
    // ============================================================================

    @Override
    @Transactional(readOnly = true)
    public AdminSyndicDashboardKpiDTO getDashboardKpis() {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime in30Days = now.plusDays(30);
        LocalDateTime thirtyDaysAgo = now.minusDays(30);

        // Total syndics + évolution — comparaison au nombre de comptes déjà inscrits il y a 30 jours
        // (createdAt est une date immuable, contrairement au statut qui n'a pas d'historique)
        long totalSyndics = userRepository.countByRole_Name(ERole.ROLE_SYNDIC);
        long totalSyndicsThirtyDaysAgo = userRepository.countByRole_NameAndCreatedAtBefore(ERole.ROLE_SYNDIC, thirtyDaysAgo);
        Double totalSyndicsVariation = totalSyndicsThirtyDaysAgo > 0
                ? (double) (totalSyndics - totalSyndicsThirtyDaysAgo) / totalSyndicsThirtyDaysAgo * 100
                : null;

        // Syndics actifs / suspendus — comptage direct sur le statut actuel du compte
        long activeSyndics = userRepository.countByRole_NameAndStatus(ERole.ROLE_SYNDIC, UserStatus.ACTIVE);
        long suspendedSyndics = userRepository.countByRole_NameAndStatus(ERole.ROLE_SYNDIC, UserStatus.DISABLED);

        // Abonnements arrivant à échéance dans les 30 prochains jours (même requête que le dashboard
        // abonnements, juste une fenêtre de 30 jours au lieu de 7)
        long expiringIn30Days = syndicSubscriptionRepository.countToRenewSoon(now, in30Days);

        // Total des résidences, toutes syndics confondues
        long totalResidences = residenceRepository.count();

        return AdminSyndicDashboardKpiDTO.builder()
                .totalSyndics(totalSyndics)
                .totalSyndicsVariation(totalSyndicsVariation)
                .activeSyndics(activeSyndics)
                // Pas d'historique des changements de statut : impossible de savoir de façon fiable
                // combien étaient "actifs" il y a 30 jours (voir commentaire sur le champ du DTO)
                .activeSyndicsVariation(null)
                .suspendedSyndics(suspendedSyndics)
                .expiringIn30Days(expiringIn30Days)
                .totalResidences(totalResidences)
                .build();
    }

    // ============================================================================
    // BLOC — LISTE DES SYNDICS
    // ============================================================================

    @Override
    @Transactional(readOnly = true)
    public AdminSyndicListResponseDTO getAllSyndics(String search, SubscriptionStatus status, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        String searchFilter = (search != null && !search.isBlank()) ? search.trim() : null;
        String statusFilter = status != null ? status.name() : null;

        Page<Object[]> resultPage = userRepository.searchSyndics(searchFilter, statusFilter, pageable);

        List<AdminSyndicRowDTO> rows = new ArrayList<>();

        // Chaque ligne est un tableau brut de colonnes, dans l'ordre exact du SELECT (user_id,
        // first_name, last_name, email, company_name, residences_count, coowners_count, plan_name,
        // expiration_date, subscription_status)
        for (Object[] row : resultPage.getContent()) {

            Long userId = ((Number) row[0]).longValue();
            String firstName = (String) row[1];
            String lastName = (String) row[2];
            String email = (String) row[3];
            String companyName = (String) row[4];
            long residencesCount = ((Number) row[5]).longValue();
            long coOwnersCount = ((Number) row[6]).longValue();
            String planName = (String) row[7];

            Timestamp expirationTimestamp = (Timestamp) row[8];
            LocalDateTime expirationDate = expirationTimestamp != null ? expirationTimestamp.toLocalDateTime() : null;

            String statusValue = (String) row[9];
            SubscriptionStatus subscriptionStatus = statusValue != null ? SubscriptionStatus.valueOf(statusValue) : null;

            rows.add(AdminSyndicRowDTO.builder()
                    .userId(userId)
                    .firstName(firstName)
                    .lastName(lastName)
                    .email(email)
                    .companyName(companyName)
                    .residencesCount(residencesCount)
                    .coOwnersCount(coOwnersCount)
                    .planName(planName)
                    .expirationDate(expirationDate)
                    .status(subscriptionStatus)
                    .statusLabel(subscriptionStatus != null ? subscriptionStatus.getLabel() : null)
                    .build());
        }

        return AdminSyndicListResponseDTO.builder()
                .totalCount(resultPage.getTotalElements())
                .syndics(rows)
                .currentPage(resultPage.getNumber())
                .totalPages(resultPage.getTotalPages())
                .build();
    }

    // ============================================================================
    // BLOC — DÉTAIL D'UN SYNDIC
    // ============================================================================

    @Override
    @Transactional(readOnly = true)
    public SyndicDetailDTO getSyndicDetail(Long syndicId) {

        User syndic = getSyndicOrThrow(syndicId);
        SyndicProfile profile = syndicProfileRepository.findByUserId(syndic.getId()).orElse(null);

        // ----- Bloc A : Informations générales -----
        SyndicGeneralInfoDTO generalInfo = SyndicGeneralInfoDTO.builder()
                .firstName(syndic.getFirstName())
                .lastName(syndic.getLastName())
                .companyName(profile != null ? profile.getCompanyName() : null)
                .email(syndic.getEmail())
                .phone(syndic.getPhone())
                .address(profile != null ? profile.getAddress() : null)
                .photoUrl(syndic.getProfilePhotoUrl())
                .registeredAt(syndic.getCreatedAt())
                .status(syndic.getStatus())
                .statusLabel(syndic.getStatus().getLabel())
                .build();

        // ----- Bloc B : KPIs -----
        long residencesCount = residenceRepository.countBySyndicId(syndicId);
        long coOwnersCount = syndicCoOwnerRelationRepository.countBySyndicId(syndicId);
        long openIncidentsCount = interventionRequestRepository.countOpenBySyndic(syndic);
        long meetingDocumentsCount = meetingDocumentRepository.countByMeetingResidenceSyndicId(syndicId);
        long meetingsCount = meetingRepository.countBySyndicId(syndicId);
        BigDecimal totalBudgetManaged = budgetRepository.sumBudgetTotalBySyndicIdAndStatus(syndicId, BudgetStatus.ACTIVE);

        SyndicDetailKpiDTO kpis = SyndicDetailKpiDTO.builder()
                .residencesCount(residencesCount)
                .coOwnersCount(coOwnersCount)
                .openIncidentsCount(openIncidentsCount)
                .meetingDocumentsCount(meetingDocumentsCount)
                .meetingsCount(meetingsCount)
                .totalBudgetManaged(totalBudgetManaged)
                .build();

        // ----- Bloc C : Abonnement en cours -----
        SyndicSubscription subscription = resolveCurrentSubscription(syndicId)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun abonnement trouvé pour ce syndic"));

        long daysRemaining = ChronoUnit.DAYS.between(LocalDateTime.now(), subscription.getEndDate());

        SyndicSubscriptionInfoDTO subscriptionInfo = SyndicSubscriptionInfoDTO.builder()
                .subscriptionId(subscription.getId())
                .planName(subscription.getSyndicPlan().getName())
                .durationLabel(subscription.getDuration().getLabel())
                .amount(subscription.getAmountPaid())
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .daysRemaining(daysRemaining)
                .status(subscription.getStatus())
                .statusLabel(subscription.getStatus().getLabel())
                .build();

        return SyndicDetailDTO.builder()
                .userId(syndic.getId())
                .generalInfo(generalInfo)
                .kpis(kpis)
                .subscription(subscriptionInfo)
                .build();
    }

    // ============================================================================
    // BLOC — RÉSIDENCES GÉRÉES PAR UN SYNDIC
    // ============================================================================

    @Override
    @Transactional(readOnly = true)
    public Page<SyndicManagedResidenceDTO> getSyndicResidences(Long syndicId, int page, int size) {

        // Vérifie que le syndic existe bien avant de lister ses résidences
        getSyndicOrThrow(syndicId);

        Pageable pageable = PageRequest.of(page, size);
        Page<Residence> residences = residenceRepository.findAllBySyndicId(syndicId, pageable);

        return residences.map(residence -> {
            long lotsCount = propertyRepository.countByResidenceId(residence.getId());
            long coOwnersCount = propertyRepository.countDistinctOwnersByResidenceId(residence.getId());
            int alertsCount = countResidenceAlerts(residence.getId());

            return SyndicManagedResidenceDTO.builder()
                    .id(residence.getId())
                    .name(residence.getName())
                    .city(residence.getCity())
                    .lotsCount(lotsCount)
                    .coOwnersCount(coOwnersCount)
                    .alertsCount(alertsCount)
                    .upToDate(alertsCount == 0)
                    .build();
        });
    }

    @Override
    @Transactional(readOnly = true)
    public ResidenceDetailDTO getResidenceDetail(Long syndicId, Long residenceId) {

        Residence residence = getResidenceOrThrow(syndicId, residenceId);

        ResidenceGeneralInfoDTO generalInfo = ResidenceGeneralInfoDTO.builder()
                .name(residence.getName())
                .fullAddress(residence.getFullAddress())
                .build();

        // Taux d'occupation = lots occupés / total des lots
        long totalLots = propertyRepository.countByResidenceId(residenceId);
        long occupiedLots = propertyRepository.countByResidenceIdAndStatus(residenceId, PropertyStatus.OCCUPIED);
        Double occupancyRate = totalLots > 0 ? (double) occupiedLots / totalLots * 100 : null;

        // Taux d'encaissement = montant encaissé / montant facturé
        BigDecimal totalDue = chargeCallItemRepository.sumQuotePartByResidenceId(residenceId);
        BigDecimal totalPaid = chargeCallItemRepository.sumPaidAmountByResidenceId(residenceId);
        Double collectionRate = totalDue.compareTo(BigDecimal.ZERO) > 0
                ? totalPaid.divide(totalDue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                : null;
        BigDecimal remainingToCollect = totalDue.subtract(totalPaid);

        // Sinistres en cours = interventions non clôturées/annulées, dont les urgentes
        long openIncidents = interventionRequestRepository.countOpenByResidenceId(residenceId);
        long urgentIncidents = interventionRequestRepository
                .countByResidenceIdAndStatusInAndUrgencyLevel(residenceId, OPEN_INTERVENTION_STATUSES, UrgencyLevel.URGENT);

        // Budget de l'année en cours (le budget ACTIVE, s'il existe)
        BigDecimal annualBudget = budgetRepository.findByResidenceIdAndStatus(residenceId, BudgetStatus.ACTIVE)
                .map(Budget::getBudgetTotal)
                .orElse(null);

        ResidenceKpiDTO kpis = ResidenceKpiDTO.builder()
                .occupancyRate(occupancyRate)
                // Pas d'historique du statut des lots conservé : "il y a un mois" n'est pas reconstituable
                .occupancyRateEvolution(null)
                .collectionRate(collectionRate)
                .remainingToCollect(remainingToCollect)
                .openIncidents(openIncidents)
                .urgentIncidents(urgentIncidents)
                .annualBudget(annualBudget)
                .build();

        return ResidenceDetailDTO.builder()
                .generalInfo(generalInfo)
                .kpis(kpis)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PropertyTypeBreakdownDTO> getResidencePropertyTypeBreakdown(Long syndicId, Long residenceId, int page, int size) {

        getResidenceOrThrow(syndicId, residenceId);

        List<Object[]> rows = propertyRepository.countByResidenceIdGroupByPropertyType(residenceId);

        List<PropertyTypeBreakdownDTO> all = rows.stream()
                .map(row -> PropertyTypeBreakdownDTO.builder()
                        .propertyTypeId(((Number) row[0]).longValue())
                        .typeLabel((String) row[1])
                        .count(((Number) row[2]).longValue())
                        .build())
                .toList();

        // Le nombre de types de biens d'un syndic reste toujours restreint (quelques types) —
        // pagination faite en mémoire plutôt que via une requête paginée dédiée
        int start = Math.min(page * size, all.size());
        int end = Math.min(start + size, all.size());

        return new PageImpl<>(all.subList(start, end), PageRequest.of(page, size), all.size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResidenceIncidentRowDTO> getResidenceRecentIncidents(Long syndicId, Long residenceId, int limit) {

        getResidenceOrThrow(syndicId, residenceId);

        List<Signalement> signalements = signalementRepository
                .findByResidenceIdOrderByCreatedAtDesc(residenceId, PageRequest.of(0, limit));

        return signalements.stream().map(this::buildResidenceIncidentRow).toList();
    }

    // ============================================================================
    // BLOC — ACTIVITÉS RÉCENTES
    // ============================================================================

    @Override
    @Transactional(readOnly = true)
    public List<ActivityRowDTO> getSyndicRecentActivities(Long syndicId, int limit) {

        getSyndicOrThrow(syndicId);

        List<ActivityLog> logs = activityLogRepository
                .findByResidenceSyndicIdOrderByCreatedAtDesc(syndicId, PageRequest.of(0, limit));

        return logs.stream().map(activityLogPresenter::buildActivityRow).toList();
    }

    // ============================================================================
    // BLOC — MÉTHODES UTILITAIRES
    // ============================================================================

    // Récupère l'admin actuellement authentifié via le contexte de sécurité Spring
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }

    // Génère une référence courte et unique, préfixée selon le type de paiement (SYN-, SUB-, CPY-...)
    private String generateReference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    // Récupère un syndic par son id, en vérifiant que ce compte a bien le rôle SYNDIC — empêche un
    // admin de consulter la "fiche syndic" d'un compte d'un autre rôle en devinant son id
    private User getSyndicOrThrow(Long syndicId) {
        User user = userRepository.findById(syndicId)
                .orElseThrow(() -> new ResourceNotFoundException("Syndic introuvable"));

        if (user.getRole().getName() != ERole.ROLE_SYNDIC) {
            throw new ResourceNotFoundException("Syndic introuvable");
        }
        return user;
    }

    // Récupère une résidence en vérifiant qu'elle appartient bien au syndic passé en paramètre.
    // Distingue les 3 causes possibles d'échec pour faciliter le débogage (outil admin interne,
    // pas une API publique où il faudrait cacher l'existence d'un id) :
    // syndic introuvable / résidence introuvable / résidence d'un autre syndic
    private Residence getResidenceOrThrow(Long syndicId, Long residenceId) {
        getSyndicOrThrow(syndicId);

        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        if (!residence.getSyndic().getId().equals(syndicId)) {
            throw new ResourceNotFoundException("Cette résidence n'appartient pas à ce syndic");
        }
        return residence;
    }

    // Résout l'abonnement "en cours" d'un syndic — ACTIVE s'il existe, sinon le plus récent (même
    // règle que partout ailleurs dans l'admin, jamais l'inverse)
    private Optional<SyndicSubscription> resolveCurrentSubscription(Long syndicId) {
        return syndicSubscriptionRepository.findFirstBySyndicIdAndStatus(syndicId, SubscriptionStatus.ACTIVE)
                .or(() -> syndicSubscriptionRepository.findFirstBySyndicIdOrderByEndDateDesc(syndicId));
    }

    // Compte le nombre de catégories d'alerte déclenchées sur une résidence précise (0 à 3) :
    // AG à venir, paiements en retard, intervention urgente non résolue
    private int countResidenceAlerts(Long residenceId) {
        int count = 0;

        if (!meetingRepository.findByResidenceIdAndStatus(residenceId, MeetingStatus.UPCOMING).isEmpty()) {
            count++;
        }
        if (chargeCallItemRepository.countLateUnpaidByResidenceId(residenceId) > 0) {
            count++;
        }
        if (interventionRequestRepository
                .countByResidenceIdAndStatusInAndUrgencyLevel(residenceId, OPEN_INTERVENTION_STATUSES, UrgencyLevel.URGENT) > 0) {
            count++;
        }
        return count;
    }

    // Construit une ligne du bloc "Derniers incidents" à partir d'un signalement
    private ResidenceIncidentRowDTO buildResidenceIncidentRow(Signalement signalement) {

        // Le "lieu" affiché dépend d'où le signalement a été fait : le numéro du lot si c'est un
        // appartement, ou le nom de l'équipement si c'est une partie commune
        String location = signalement.getLocationType() == IncidentLocationType.APPARTEMENT
                ? (signalement.getProperty() != null ? signalement.getProperty().getReference() : null)
                : (signalement.getCommonFacility() != null ? signalement.getCommonFacility().getFacilityType().getName() : null);

        return ResidenceIncidentRowDTO.builder()
                .id(signalement.getId())
                .title(signalement.getTitle())
                .statusLabel(signalement.getStatus().getLabel())
                .priorityLabel(signalement.getUrgencyLevel().getLabel())
                .date(signalement.getCreatedAt())
                .location(location)
                .build();
    }
}

package com.example.solimus.services.admin.subscription;

import com.example.solimus.dtos.admin.subscription.*;
import com.example.solimus.dtos.syndic.subscription.SyndicPlanChangeResponseDTO;
import com.example.solimus.dtos.syndic.subscription.SyndicSubscriptionHistoryDTO;
import com.example.solimus.entities.ProviderPlan;
import com.example.solimus.entities.ProviderProfile;
import com.example.solimus.entities.ProviderSubscription;
import com.example.solimus.entities.SyndicPlan;
import com.example.solimus.entities.SyndicProfile;
import com.example.solimus.entities.SyndicSubscription;
import com.example.solimus.entities.User;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.ProviderPlanFeature;
import com.example.solimus.enums.SyndicPlanFeature;
import com.example.solimus.enums.SubscriberType;
import com.example.solimus.enums.SubscriptionDuration;
import com.example.solimus.enums.SubscriptionStatus;
import com.example.solimus.enums.UserStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.ProviderPlanRepository;
import com.example.solimus.repositories.ProviderProfileRepository;
import com.example.solimus.repositories.ProviderSubscriptionRepository;
import com.example.solimus.repositories.SubscriberRepository;
import com.example.solimus.repositories.SyndicPlanRepository;
import com.example.solimus.repositories.SyndicProfileRepository;
import com.example.solimus.repositories.SyndicSubscriptionRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.auth.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PlanServiceImpl implements PlanService {

    private final SyndicPlanRepository syndicPlanRepository;
    private final SyndicSubscriptionRepository syndicSubscriptionRepository;
    private final ProviderPlanRepository providerPlanRepository;
    private final ProviderSubscriptionRepository providerSubscriptionRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final SubscriberRepository subscriberRepository;
    private final SyndicProfileRepository syndicProfileRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.touchpay.bridge-url}")
    private String touchPayBridgeUrlTemplate;


    // ============================================================================
    // BLOC — MÉTHODES SYNDIC (formules syndic uniquement)
    // ============================================================================

    // =========================================================================
    // Création d'une nouvelle formule syndic
    // =========================================================================
    @Override
    @Transactional
    public SyndicPlanDTO createSyndicPlan(SyndicPlanRequestDTO dto) {

        // Empêche deux formules syndic portant le même nom
        if (syndicPlanRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new BadRequestException("Une formule syndic portant ce nom existe déjà");
        }

        SyndicPlan plan = new SyndicPlan();

        plan.setName(dto.getName());
        plan.setDescription(dto.getDescription());
        plan.setMonthlyPrice(dto.getMonthlyPrice());
        plan.setYearlyPrice(dto.getYearlyPrice());
        plan.setMaxResidences(dto.getMaxResidences());
        plan.setMaxApartments(dto.getMaxApartments());
        plan.setFeatures(dto.getFeatures() != null ? dto.getFeatures() : new HashSet<>());
        plan.setActive(dto.getActive() != null ? dto.getActive() : true);

        SyndicPlan saved = syndicPlanRepository.save(plan);

        return toDTO(saved);
    }

    // Mise à jour d'une formule syndic existante
    @Override
    @Transactional
    public SyndicPlanDTO updateSyndicPlan(Long id, SyndicPlanRequestDTO dto) {

        SyndicPlan plan = syndicPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Formule introuvable"));

        // Ne met à jour que les champs réellement envoyés
        if (dto.getName() != null) {
            // Empêche de renommer cette formule avec le nom d'une autre formule syndic existante
            if (syndicPlanRepository.existsByNameIgnoreCaseAndIdNot(dto.getName(), id)) {
                throw new BadRequestException("Une formule syndic portant ce nom existe déjà");
            }
            plan.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            plan.setDescription(dto.getDescription());
        }
        if (dto.getMonthlyPrice() != null) {
            plan.setMonthlyPrice(dto.getMonthlyPrice());
        }
        if (dto.getYearlyPrice() != null) {
            plan.setYearlyPrice(dto.getYearlyPrice());
        }
        if (dto.getMaxResidences() != null) {
            plan.setMaxResidences(dto.getMaxResidences());
        }
        if (dto.getMaxApartments() != null) {
            plan.setMaxApartments(dto.getMaxApartments());
        }
        if (dto.getFeatures() != null) {
            plan.setFeatures(dto.getFeatures());
        }
        if (dto.getActive() != null) {
            plan.setActive(dto.getActive());
        }

        SyndicPlan saved = syndicPlanRepository.save(plan);

        return toDTO(saved);
    }

    // Suppression d'une formule syndic
    @Override
    @Transactional
    public void deleteSyndicPlan(Long id) {

        SyndicPlan plan = syndicPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Formule introuvable"));

        // Empêche la suppression si des abonnés existent encore sur cette formule
        long subscribersCount = syndicSubscriptionRepository.countBySyndicPlanId(id);
        if (subscribersCount > 0) {
            throw new BadRequestException("Impossible de supprimer une formule ayant encore des abonnés actifs");
        }

        syndicPlanRepository.delete(plan);
    }

    // Active ou désactive une formule syndic (sans la supprimer)
    @Override
    @Transactional
    public SyndicPlanDTO toggleSyndicPlanStatus(Long id, boolean active) {

        SyndicPlan plan = syndicPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Formule introuvable"));

        plan.setActive(active);
        SyndicPlan saved = syndicPlanRepository.save(plan);

        return toDTO(saved);
    }

    // Construit les 4 dates du cycle de vie en ne regardant QUE la transition qui a produit
    // l'abonnement actuel (current vs celui juste avant lui) . Donc soit
    // "Renouvellement", soit "Changement de formule" est rempli
    private SubscriberLifecycleDTO buildLifecycle(List<SyndicSubscription> historyAsc, SyndicSubscription current) {

        // Retrouve la position de l'abonnement actuel dans l'historique
        int currentIndex = 0;
        for (int i = 0; i < historyAsc.size(); i++) {
            if (historyAsc.get(i).getId().equals(current.getId())) {
                currentIndex = i;
                break;
            }
        }

        // Date du dernier renouvellement (si applicable)
        LocalDateTime renewalDate = null;

        // Date du dernier changement de formule (si applicable)
        LocalDateTime planChangeDate = null;

        // Compare uniquement l'abonnement actuel avec celui qui le précède
        if (currentIndex > 0) {
            SyndicSubscription previous = historyAsc.get(currentIndex - 1);

            // Vérifie si le syndic est resté sur la même formule
            boolean samePlanAsBefore = current.getSyndicPlan().getId().equals(previous.getSyndicPlan().getId());

            // Même formule = renouvellement
            if (samePlanAsBefore) {
                renewalDate = current.getStartDate();

                // Formule différente = changement de formule
            } else {
                planChangeDate = current.getStartDate();
            }
        }

        return SubscriberLifecycleDTO.builder()

                // Date de la toute première souscription
                .initialSubscriptionDate(historyAsc.get(0).getStartDate())

                // Date du dernier renouvellement (si applicable)
                .renewalDate(renewalDate)

                // Date du dernier changement de formule (si applicable)
                .planChangeDate(planChangeDate)

                // Date de fin de l'abonnement actuel
                .expectedExpirationDate(current.getEndDate())

                .build();
    }

    // Convertit une entité SyndicPlan en DTO d'affichage
    private SyndicPlanDTO toDTO(SyndicPlan plan) {

        List<SyndicPlanFeatureDTO> featureDTOs = new ArrayList<>();
        for (SyndicPlanFeature feature : plan.getFeatures()) {
            featureDTOs.add(SyndicPlanFeatureDTO.builder()
                    .value(feature.name())
                    .label(feature.getLabel())
                    .build());
        }

        long subscribersCount = syndicSubscriptionRepository.countBySyndicPlanId(plan.getId());

        return SyndicPlanDTO.builder()
                .id(plan.getId())
                .name(plan.getName())
                .description(plan.getDescription())
                .monthlyPrice(plan.getMonthlyPrice())
                .yearlyPrice(plan.getYearlyPrice())
                .maxResidences(plan.getMaxResidences())
                .maxApartments(plan.getMaxApartments())
                .features(featureDTOs)
                .active(plan.getActive())
                .subscribersCount(subscribersCount)
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }

    // ============================================================================
    // BLOC — MÉTHODES PRESTATAIRE (formules prestataire uniquement)
    // ============================================================================

    // Création d'une nouvelle formule prestataire
    @Override
    @Transactional
    public ProviderPlanDTO createProviderPlan(ProviderPlanRequestDTO dto) {

        // Empêche deux formules prestataire portant le même nom
        if (providerPlanRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new BadRequestException("Une formule prestataire portant ce nom existe déjà");
        }

        ProviderPlan plan = new ProviderPlan();
        plan.setName(dto.getName());
        plan.setDescription(dto.getDescription());
        plan.setMonthlyPrice(dto.getMonthlyPrice());
        plan.setYearlyPrice(dto.getYearlyPrice());
        plan.setFeatures(dto.getFeatures() != null ? dto.getFeatures() : new HashSet<>());
        plan.setActive(dto.getActive() != null ? dto.getActive() : true);

        ProviderPlan saved = providerPlanRepository.save(plan);
        return toProviderDTO(saved);
    }

    // Mise à jour d'une formule prestataire précise
    @Override
    @Transactional
    public ProviderPlanDTO updateProviderPlan(Long id, ProviderPlanRequestDTO dto) {

        ProviderPlan plan = providerPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Formule introuvable"));

        if (dto.getName() != null) {
            // Empêche de renommer cette formule avec le nom d'une autre formule prestataire existante
            if (providerPlanRepository.existsByNameIgnoreCaseAndIdNot(dto.getName(), id)) {
                throw new BadRequestException("Une formule prestataire portant ce nom existe déjà");
            }
            plan.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            plan.setDescription(dto.getDescription());
        }
        if (dto.getMonthlyPrice() != null) {
            plan.setMonthlyPrice(dto.getMonthlyPrice());
        }
        if (dto.getYearlyPrice() != null) {
            plan.setYearlyPrice(dto.getYearlyPrice());
        }
        if (dto.getFeatures() != null) {
            plan.setFeatures(dto.getFeatures());
        }
        if (dto.getActive() != null) {
            plan.setActive(dto.getActive());
        }

        ProviderPlan saved = providerPlanRepository.save(plan);
        return toProviderDTO(saved);
    }

    // Active ou désactive une formule prestataire précise
    @Override
    @Transactional
    public ProviderPlanDTO toggleProviderPlanStatus(Long id, boolean active) {

        ProviderPlan plan = providerPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Formule introuvable"));

        plan.setActive(active);
        ProviderPlan saved = providerPlanRepository.save(plan);
        return toProviderDTO(saved);
    }

    // Suppression d'une formule prestataire précise
    @Override
    @Transactional
    public void deleteProviderPlan(Long id) {

        ProviderPlan plan = providerPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Formule introuvable"));

        long subscribersCount = providerSubscriptionRepository.countByProviderPlanId(id);
        if (subscribersCount > 0) {
            throw new BadRequestException("Impossible de supprimer une formule ayant encore des abonnés actifs");
        }

        providerPlanRepository.delete(plan);
    }

    // Même logique que getSubscriberDetail, mais pour un prestataire
    private SubscriberDetailDTO getProviderSubscriberDetail(Long subscriptionId) {

        // Récupère l'abonnement demandé
        ProviderSubscription current = providerSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonnement introuvable"));

        // Récupère le prestataire lié à cet abonnement
        User provider = current.getProvider();

        // Récupère son profil prestataire (s'il existe)
        ProviderProfile profile = providerProfileRepository.findByUserId(provider.getId()).orElse(null);

        // Récupère tout l'historique des abonnements du prestataire
        List<ProviderSubscription> history = providerSubscriptionRepository.findByProviderIdOrderByCreatedAtAsc(provider.getId());

        // Utilise le nom de l'entreprise si renseigné, sinon le nom complet du prestataire
        String clientName = profile != null && profile.getCompanyName() != null
                ? profile.getCompanyName()
                : provider.getFirstName() + " " + provider.getLastName();

        return SubscriberDetailDTO.builder()
                .profilePhotoUrl(provider.getProfilePhotoUrl())
                .clientName(clientName)
                .city(provider.getCity())
                .country(provider.getCountry())
                .subscriberType(SubscriberType.PRESTATAIRE)
                .entityTypeLabel("Prestataire de services")
                .status(current.getStatus())
                .statusLabel(current.getStatus().getLabel())
                .memberSince(provider.getCreatedAt())
                .responsibleName(provider.getFirstName() + " " + provider.getLastName())
                .planName(current.getProviderPlan().getName())
                .amount(current.getAmountPaid())
                .durationLabel(current.getDuration().getLabel())

                // Date de la première souscription
                .subscriptionDate(history.get(0).getStartDate())

                // Date d'expiration de l'abonnement actuel
                .expirationDate(current.getEndDate())

                .paymentMethodLabel(current.getMethod() != null ? current.getMethod().getLabel() : null)

                // Construit le cycle de vie de l'abonnement
                .lifecycle(buildProviderLifecycle(history, current))
                .build();
    }

    // Même logique que buildLifecycle, mais pour l'historique d'un prestataire
    private SubscriberLifecycleDTO buildProviderLifecycle(List<ProviderSubscription> historyAsc, ProviderSubscription current) {

        // Retrouve la position de l'abonnement actuel dans l'historique
        int currentIndex = 0;
        for (int i = 0; i < historyAsc.size(); i++) {
            if (historyAsc.get(i).getId().equals(current.getId())) {
                currentIndex = i;
                break;
            }
        }

        // Date du dernier renouvellement (si applicable)
        LocalDateTime renewalDate = null;

        // Date du dernier changement de formule (si applicable)
        LocalDateTime planChangeDate = null;

        // Compare uniquement l'abonnement actuel avec celui qui le précède
        if (currentIndex > 0) {
            ProviderSubscription previous = historyAsc.get(currentIndex - 1);

            // Vérifie si le prestataire est resté sur la même formule
            boolean samePlanAsBefore = current.getProviderPlan().getId().equals(previous.getProviderPlan().getId());

            // Même formule = renouvellement
            if (samePlanAsBefore) {
                renewalDate = current.getStartDate();

                // Formule différente = changement de formule
            } else {
                planChangeDate = current.getStartDate();
            }
        }

        return SubscriberLifecycleDTO.builder()

                // Date de la toute première souscription
                .initialSubscriptionDate(historyAsc.get(0).getStartDate())

                // Date du dernier renouvellement (si applicable)
                .renewalDate(renewalDate)

                // Date du dernier changement de formule (si applicable)
                .planChangeDate(planChangeDate)

                // Date de fin de l'abonnement actuel
                .expectedExpirationDate(current.getEndDate())

                .build();
    }

    /**
     * Conversion entité → DTO.
     */
    private ProviderPlanDTO toProviderDTO (ProviderPlan plan) {

        List<String> featureLabels = new ArrayList<>();
        for (ProviderPlanFeature feature : plan.getFeatures()) {
            featureLabels.add(feature.getLabel());
        }

        return ProviderPlanDTO.builder()
                .id(plan.getId())
                .name(plan.getName())
                .description(plan.getDescription())
                .monthlyPrice(plan.getMonthlyPrice())
                .yearlyPrice(plan.getYearlyPrice())
                .active(plan.getActive())
                .featureLabels(featureLabels)
                .updatedAt(plan.getUpdatedAt())
                .build();
    }

    // ============================================================================
    // BLOC — MÉTHODES PARTAGÉES (Syndic + Prestataire, dispatchent selon subscriberType)
    // ============================================================================

    // =========================================================================
    // Liste unifiée de toutes les formules (Syndic + Prestataire)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public List<PlanOverviewDTO> getAllPlansOverview() {

        List<PlanOverviewDTO> allPlans = new ArrayList<>();

        // Bloc 1 : toutes les formules syndic
        List<SyndicPlan> syndicPlans = syndicPlanRepository.findAll();
        for (SyndicPlan plan : syndicPlans) {

            //Les fonctionnalités à cocher
            List<String> featureLabels = new ArrayList<>();
            for (SyndicPlanFeature feature : plan.getFeatures()) {
                featureLabels.add(feature.getLabel());
            }

            //Compte le nombre de Syndic ayant le plan spécifié
            long subscribersCount = syndicSubscriptionRepository.countBySyndicPlanId(plan.getId());

            allPlans.add(PlanOverviewDTO.builder()
                    .id(plan.getId())
                    .planType(SubscriberType.SYNDIC)
                    .name(plan.getName())
                    .monthlyPrice(plan.getMonthlyPrice())
                    .yearlyPrice(plan.getYearlyPrice())
                    .featureLabels(featureLabels)
                    .active(plan.getActive())
                    .subscribersCount(subscribersCount)
                    .build());
        }

        // Bloc 2 : toutes les formules prestataires
        List<ProviderPlan> providerPlans = providerPlanRepository.findAll();
        for (ProviderPlan providerPlan : providerPlans) {

            long providerSubscribersCount = providerSubscriptionRepository.countByProviderPlanId(providerPlan.getId());

            List<String> providerFeatureLabels = new ArrayList<>();
            for (ProviderPlanFeature feature : providerPlan.getFeatures()) {
                providerFeatureLabels.add(feature.getLabel());
            }

            allPlans.add(PlanOverviewDTO.builder()
                    .id(providerPlan.getId())
                    .planType(SubscriberType.PRESTATAIRE)
                    .name(providerPlan.getName())
                    .monthlyPrice(providerPlan.getMonthlyPrice())
                    .yearlyPrice(providerPlan.getYearlyPrice())
                    .featureLabels(providerFeatureLabels)
                    .active(providerPlan.getActive())
                    .subscribersCount(providerSubscribersCount)
                    .build());
        }

        return allPlans;
    }

    // =========================================================================
    // KPIs de la page Gestion des abonnements
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public SubscriptionKpiDTO getSubscriptionKpis() {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime in7Days = now.plusDays(7);
        LocalDateTime startOfMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime last30Days = now.minusDays(30);

        long activeSubscriptions = providerSubscriptionRepository.countCurrentlyActive(now)
                + syndicSubscriptionRepository.countCurrentlyActive(now);

        long expiredSubscriptions = providerSubscriptionRepository.countCurrentlyExpiredWithoutRenewal()
                + syndicSubscriptionRepository.countCurrentlyExpiredWithoutRenewal();

        long toRenewSubscriptions = providerSubscriptionRepository.countToRenewSoon(now, in7Days)
                + syndicSubscriptionRepository.countToRenewSoon(now, in7Days);

        BigDecimal monthlyRevenue = providerSubscriptionRepository.sumAmountPaidInPeriod(startOfMonth, now)
                .add(syndicSubscriptionRepository.sumAmountPaidInPeriod(startOfMonth, now));

        long renewedTotal = providerSubscriptionRepository.countRenewedInPeriod(last30Days, now)
                + syndicSubscriptionRepository.countRenewedInPeriod(last30Days, now);
        long expiredTotal = providerSubscriptionRepository.countExpiredInPeriod(last30Days, now)
                + syndicSubscriptionRepository.countExpiredInPeriod(last30Days, now);

        Double renewalRate = expiredTotal > 0
                ? (double) renewedTotal / expiredTotal * 100
                : null;

        long activeThirtyDaysAgo = providerSubscriptionRepository.countActiveAsOf(last30Days)
                + syndicSubscriptionRepository.countActiveAsOf(last30Days);

        Double activeSubscriptionsVariation = activeThirtyDaysAgo > 0
                ? (double) (activeSubscriptions - activeThirtyDaysAgo) / activeThirtyDaysAgo * 100
                : null;

        long expiredThirtyDaysAgo = providerSubscriptionRepository.countExpiredWithoutRenewalAsOf(last30Days)
                + syndicSubscriptionRepository.countExpiredWithoutRenewalAsOf(last30Days);

        Double expiredSubscriptionsVariation = expiredThirtyDaysAgo > 0
                ? (double) (expiredSubscriptions - expiredThirtyDaysAgo) / expiredThirtyDaysAgo * 100
                : null;

        return SubscriptionKpiDTO.builder()
                .activeSubscriptions(activeSubscriptions)
                .activeSubscriptionsVariation(activeSubscriptionsVariation)
                .expiredSubscriptions(expiredSubscriptions)
                .expiredSubscriptionsVariation(expiredSubscriptionsVariation)
                .toRenewSubscriptions(toRenewSubscriptions)
                .monthlyRevenue(monthlyRevenue)
                .renewalRate(renewalRate)
                .build();
    }

    // =========================================================================
    // Liste unifiée des abonnés (Syndic + Prestataire), avec recherche et filtres
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public SubscriberListResponseDTO getAllSubscribers(String search, SubscriptionStatus status,
                                                         SubscriberType subscriberType, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        // Nettoie le texte de recherche
        String searchFilter = null;
        if (search != null && !search.isBlank()) {
            searchFilter = search.trim();
        }

        // Convertit les enums en texte brut pour la requête native (comparaison exacte)
        String statusFilter = status != null ? status.name() : null;
        String subscriberTypeFilter = subscriberType != null ? subscriberType.name() : null;

        Page<Object[]> resultPage = subscriberRepository.searchSubscribers(
                searchFilter, statusFilter, subscriberTypeFilter, pageable);

        List<SubscriberRowDTO> rows = new ArrayList<>();

        // Chaque ligne est un tableau brut de colonnes, il faut extraire chaque valeur une par une,
        // dans l'ordre exact du SELECT (subscriber_type, subscription_id, client_name, client_email,
        // city, country, plan_name, amount, start_date, end_date, status)
        for (Object[] row : resultPage.getContent()) {

            String subscriberTypeValue = (String) row[0];
            Long subscriptionId = ((Number) row[1]).longValue();
            String clientName = (String) row[2];
            String clientEmail = (String) row[3];
            String city = (String) row[4];
            String country = (String) row[5];
            String planName = (String) row[6];
            BigDecimal amount = (BigDecimal) row[7];

            Timestamp startTimestamp = (Timestamp) row[8];
            LocalDateTime startDate = startTimestamp != null ? startTimestamp.toLocalDateTime() : null;

            Timestamp endTimestamp = (Timestamp) row[9];
            LocalDateTime endDate = endTimestamp != null ? endTimestamp.toLocalDateTime() : null;

            String statusValue = (String) row[10];
            SubscriptionStatus subscriptionStatus = SubscriptionStatus.valueOf(statusValue);

            rows.add(SubscriberRowDTO.builder()
                    .subscriptionId(subscriptionId)
                    .subscriberType(SubscriberType.valueOf(subscriberTypeValue))
                    .clientName(clientName)
                    .clientEmail(clientEmail)
                    .city(city)
                    .country(country)
                    .planName(planName)
                    .amount(amount)
                    .startDate(startDate)
                    .endDate(endDate)
                    .status(subscriptionStatus)
                    .statusLabel(subscriptionStatus.getLabel())
                    .build());
        }

        return SubscriberListResponseDTO.builder()
                .totalCount(resultPage.getTotalElements())
                .subscribers(rows)
                .currentPage(resultPage.getNumber())
                .totalPages(resultPage.getTotalPages())
                .build();
    }

    // =========================================================================
    // Détail d'un abonné précis, ouvert via l'icône œil de la liste des abonnés
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public SubscriberDetailDTO getSubscriberDetail(Long subscriptionId, SubscriberType subscriberType) {

        if (subscriberType == SubscriberType.PRESTATAIRE) {
            return getProviderSubscriberDetail(subscriptionId);
        }

        // L'abonnement cliqué depuis la liste est déjà celui à afficher (la liste ne renvoie que
        // l'abonnement ACTIVE s'il y en a un, sinon le plus récent)
        SyndicSubscription current = syndicSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonnement introuvable"));

        User syndic = current.getSyndic();
        SyndicProfile profile = syndicProfileRepository.findByUserId(syndic.getId()).orElse(null);

        // Tout l'historique de ce syndic, du plus ancien au plus récent, pour reconstituer le cycle de vie
        List<SyndicSubscription> history = syndicSubscriptionRepository.findBySyndicIdOrderByCreatedAtAsc(syndic.getId());

        // Nom affiché : la société si elle est renseignée, sinon prénom + nom (même règle que la liste)
        String clientName = profile != null && profile.getCompanyName() != null
                ? profile.getCompanyName()
                : syndic.getFirstName() + " " + syndic.getLastName();

        return SubscriberDetailDTO.builder()
                .profilePhotoUrl(syndic.getProfilePhotoUrl())
                .clientName(clientName)
                .city(syndic.getCity())
                .country(syndic.getCountry())
                .subscriberType(SubscriberType.SYNDIC)
                .entityTypeLabel("Syndic de Copropriété")
                .status(current.getStatus())
                .statusLabel(current.getStatus().getLabel())
                .memberSince(syndic.getCreatedAt())
                .responsibleName(syndic.getFirstName() + " " + syndic.getLastName())
                .planName(current.getSyndicPlan().getName())
                .amount(current.getAmountPaid())
                .durationLabel(current.getDuration().getLabel())
                .subscriptionDate(history.get(0).getStartDate())// La date de début du tout premier abonnement de ce syndic
                .expirationDate(current.getEndDate())// La date de fin de l'abonnement actuellement en cours (ou le plus récent si aucun n'est actif)
                .paymentMethodLabel(current.getMethod() != null ? current.getMethod().getLabel() : null)
                .lifecycle(buildLifecycle(history, current))
                .build();
    }

    // =========================================================================
    // Bloc "Détails du client" léger — affiché en haut des modales d'action
    // (Suspendre, Réactiver...)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public SubscriberQuickInfoDTO getSubscriberQuickInfo(Long subscriptionId, SubscriberType subscriberType) {

        if (subscriberType == SubscriberType.PRESTATAIRE) {

            ProviderSubscription subscription = providerSubscriptionRepository.findById(subscriptionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Abonnement introuvable"));
            User provider = subscription.getProvider();
            ProviderProfile profile = providerProfileRepository.findByUserId(provider.getId()).orElse(null);

            String providerClientName = profile != null && profile.getCompanyName() != null
                    ? profile.getCompanyName()
                    : provider.getFirstName() + " " + provider.getLastName();

            return SubscriberQuickInfoDTO.builder()
                    .clientName(providerClientName)
                    .subscriberTypeLabel(SubscriberType.PRESTATAIRE.getLabel())
                    .planName(subscription.getProviderPlan().getName())
                    .status(subscription.getStatus())
                    .statusLabel(subscription.getStatus().getLabel())
                    .build();
        }
        // Sinon si Syndic
        SyndicSubscription subscription = syndicSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonnement introuvable"));

        User syndic = subscription.getSyndic();
        SyndicProfile profile = syndicProfileRepository.findByUserId(syndic.getId()).orElse(null);

        String clientName = profile != null && profile.getCompanyName() != null
                ? profile.getCompanyName()
                : syndic.getFirstName() + " " + syndic.getLastName();

        return SubscriberQuickInfoDTO.builder()
                .clientName(clientName)
                .subscriberTypeLabel(SubscriberType.SYNDIC.getLabel())
                .planName(subscription.getSyndicPlan().getName())
                .status(subscription.getStatus())
                .statusLabel(subscription.getStatus().getLabel())
                .build();
    }

    // =========================================================================
    // Bloc "Détails du client" léger — affiché en haut de la modale "Renouveler l'abonnement"
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public SubscriberRenewalInfoDTO getSubscriberRenewalInfo(Long subscriptionId, SubscriberType subscriberType) {

        if (subscriberType == SubscriberType.PRESTATAIRE) {

            ProviderSubscription subscription = providerSubscriptionRepository.findById(subscriptionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Abonnement introuvable"));
            User provider = subscription.getProvider();
            ProviderProfile profile = providerProfileRepository.findByUserId(provider.getId()).orElse(null);

            String providerClientName = profile != null && profile.getCompanyName() != null
                    ? profile.getCompanyName()
                    : provider.getFirstName() + " " + provider.getLastName();

            long providerDaysRemaining = ChronoUnit.DAYS.between(LocalDateTime.now(), subscription.getEndDate());

            return SubscriberRenewalInfoDTO.builder()
                    .clientName(providerClientName)
                    .subscriberTypeLabel(SubscriberType.PRESTATAIRE.getLabel())
                    .currentPlanName(subscription.getProviderPlan().getName())
                    .expirationDate(subscription.getEndDate())
                    .daysRemaining(providerDaysRemaining)
                    .build();
        }
        //sinon si syndic
        SyndicSubscription subscription = syndicSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonnement introuvable"));

        User syndic = subscription.getSyndic();
        SyndicProfile profile = syndicProfileRepository.findByUserId(syndic.getId()).orElse(null);

        String clientName = profile != null && profile.getCompanyName() != null
                ? profile.getCompanyName()
                : syndic.getFirstName() + " " + syndic.getLastName();

        // Calculé à la volée: nombre de jours restants avant la date de fin de l'abonnement actuel
        long daysRemaining = ChronoUnit.DAYS.between(
                LocalDateTime.now(), subscription.getEndDate());

        return SubscriberRenewalInfoDTO.builder()
                .clientName(clientName)
                .subscriberTypeLabel(SubscriberType.SYNDIC.getLabel())
                .currentPlanName(subscription.getSyndicPlan().getName())
                .expirationDate(subscription.getEndDate())
                .daysRemaining(daysRemaining)
                .build();
    }

    // =========================================================================
    // Options du formulaire de renouvellement — formules actives + durées disponibles
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public RenewalFormOptionsDTO getRenewalFormOptions(SubscriberType subscriberType) {

        // Liste les formules actives, syndic ou prestataire selon le type demandé
        List<RenewalFormOptionsDTO.PlanOption> plans = subscriberType == SubscriberType.PRESTATAIRE
                ? providerPlanRepository.findByActiveTrue().stream()
                        .map(plan -> RenewalFormOptionsDTO.PlanOption.builder()
                                .id(plan.getId())
                                .label(plan.getName())
                                .build())
                        .toList()
                : syndicPlanRepository.findByActiveTrue().stream()
                        .map(plan -> RenewalFormOptionsDTO.PlanOption.builder()
                                .id(plan.getId())
                                .label(plan.getName())
                                .build())
                        .toList();

        // Liste toutes les durées disponibles, pour le select du formulaire
        List<RenewalFormOptionsDTO.DurationOption> durations = Arrays.stream(SubscriptionDuration.values())
                .map(duration -> RenewalFormOptionsDTO.DurationOption.builder()
                        .value(duration.name())
                        .label(duration.getLabel())
                        .build())
                .toList();

        return RenewalFormOptionsDTO.builder()
                .plans(plans)
                .durations(durations)
                .build();
    }

    // =========================================================================
    // Historique des paiements d'un abonné précis
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public Page<SyndicSubscriptionHistoryDTO> getSubscriberPaymentHistory(Long subscriptionId, SubscriberType subscriberType,
                                                                          int page, int size) {
        // Construit les informations de pagination
        Pageable pageable = PageRequest.of(page, size);

        // Cas où l'abonné est un prestataire
        if (subscriberType == SubscriberType.PRESTATAIRE) {

            // Récupère l'abonnement sélectionné
            ProviderSubscription providerSubscription = providerSubscriptionRepository.findById(subscriptionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Abonnement introuvable"));

            // Récupère l'identifiant du prestataire
            Long providerId = providerSubscription.getProvider().getId();

            // Récupère l'historique des abonnements du prestataire (du plus récent au plus ancien)
            Page<ProviderSubscription> providerHistory = providerSubscriptionRepository
                    .findByProviderIdOrderByCreatedAtDesc(providerId, pageable);

            // Transforme chaque abonnement en ligne d'historique pour la réponse
            return providerHistory.map(entry -> SyndicSubscriptionHistoryDTO.builder()

                    // Date de création de l'abonnement
                    .date(entry.getCreatedAt())

                    // Nom de la formule souscrite
                    .planName(entry.getProviderPlan().getName())

                    // Montant payé
                    .amount(entry.getAmountPaid())

                    // Moyen de paiement utilisé
                    .paymentMethodLabel(entry.getMethod() != null ? entry.getMethod().getLabel() : null)

                    // Libellé du statut de paiement
                    .statusLabel(mapPaymentStatusToLabel(entry.getPaymentStatus()))

                    .build());
        }
        // Retrouve le syndic concerné à partir de l'abonnement cliqué sur la liste
        SyndicSubscription subscription = syndicSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonnement introuvable"));
        Long syndicId = subscription.getSyndic().getId();

        // Toutes ses tentatives d'abonnement (payées, échouées ou en attente), du plus récent au plus ancien
        Page<SyndicSubscription> history = syndicSubscriptionRepository
                .findBySyndicIdOrderByCreatedAtDesc(syndicId, pageable);

        return history.map(entry -> SyndicSubscriptionHistoryDTO.builder()
                .date(entry.getCreatedAt())
                .planName(entry.getSyndicPlan().getName())
                .amount(entry.getAmountPaid())
                .paymentMethodLabel(entry.getMethod() != null ? entry.getMethod().getLabel() : null)
                .statusLabel(mapPaymentStatusToLabel(entry.getPaymentStatus()))
                .build());
    }

    // =========================================================================
    // Suspension d'un compte abonné — bloque le login (User.status = DISABLED) et désactive
    // l'abonnement (SubscriptionStatus.DESACTIVATED) ensemble, pour rester cohérent avec la liste
    // des abonnés et les KPIs, qui se basent sur le statut de l'abonnement
    // =========================================================================
    @Override
    @Transactional
    public void suspendSubscriber(Long subscriptionId, SubscriberType subscriberType, SuspendSubscriberDTO dto) {

        //Cas Prestataire
        if (subscriberType == SubscriberType.PRESTATAIRE) {

            ProviderSubscription providerSubscription = providerSubscriptionRepository.findById(subscriptionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Abonnement introuvable"));
            User provider = providerSubscription.getProvider();

            provider.setStatus(UserStatus.DISABLED);
            provider.setSuspensionReason(dto.getReason());
            provider.setSuspendedAt(LocalDateTime.now());
            userRepository.save(provider);

            providerSubscription.setStatus(SubscriptionStatus.DESACTIVATED);
            providerSubscriptionRepository.save(providerSubscription);

            if (dto.isNotifyClient()) {
                String subject = "Votre compte Solimus a été suspendu";
                String body = "Bonjour " + provider.getFirstName() + ",\n\n" +
                        "Votre compte a été suspendu par un administrateur.\n" +
                        "Motif : " + dto.getReason() + "\n\n" +
                        "Pour toute question, veuillez contacter l'administrateur.\n\n" +
                        "Cordialement,\nL'équipe Solimus";
                emailService.sendEmail(provider.getEmail(), subject, body);
            }
            return;
        }

        //CAS Syndic
        SyndicSubscription subscription = syndicSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonnement introuvable"));

        User syndic = subscription.getSyndic();

        // Bloque le login — le message "Votre compte a été désactivé..." est déjà géré par AuthServiceImpl
        syndic.setStatus(UserStatus.DISABLED);
        syndic.setSuspensionReason(dto.getReason());
        syndic.setSuspendedAt(LocalDateTime.now());
        userRepository.save(syndic);

        // Désactive l'abonnement pour qu'il n'apparaisse plus "ACTIF" dans la liste ni dans les KPIs
        subscription.setStatus(SubscriptionStatus.DESACTIVATED);
        syndicSubscriptionRepository.save(subscription);

        if (dto.isNotifyClient()) {
            String subject = "Votre compte Solimus a été suspendu";
            String body = "Bonjour " + syndic.getFirstName() + ",\n\n" +
                    "Votre compte a été suspendu par un administrateur.\n" +
                    "Motif : " + dto.getReason() + "\n\n" +
                    "Pour toute question, veuillez contacter l'administrateur.\n\n" +
                    "Cordialement,\nL'équipe Solimus";
            emailService.sendEmail(syndic.getEmail(), subject, body);
        }
    }

    // =========================================================================
    // Réactivation d'un compte précédemment suspendu — inverse exactement ce que fait la suspension.
    // Le motif/date de la suspension restent en base (historique), on ne fait que rebasculer les statuts.
    // =========================================================================
    @Override
    @Transactional
    public void reactivateSubscriber(Long subscriptionId, SubscriberType subscriberType, boolean notifyClient) {

        //Cas Prestataire
        if (subscriberType == SubscriberType.PRESTATAIRE) {

            ProviderSubscription providerSubscription = providerSubscriptionRepository.findById(subscriptionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Abonnement introuvable"));
            User provider = providerSubscription.getProvider();

            if (provider.getStatus() != UserStatus.DISABLED) {
                throw new BadRequestException("Ce compte n'est pas suspendu");
            }

            provider.setStatus(UserStatus.ACTIVE);
            userRepository.save(provider);

            // Si la date de fin est déjà dépassée, le scheduler d'expiration horaire la rebasculera en EXPIRED tout seul
            providerSubscription.setStatus(SubscriptionStatus.ACTIVE);
            providerSubscriptionRepository.save(providerSubscription);

            if (notifyClient) {
                String subject = "Votre compte Solimus a été réactivé";
                String body = "Bonjour " + provider.getFirstName() + ",\n\n" +
                        "Votre compte a été réactivé par un administrateur. Vous pouvez de nouveau vous connecter.\n\n" +
                        "Cordialement,\nL'équipe Solimus";
                emailService.sendEmail(provider.getEmail(), subject, body);
            }
            return;
        }

        //Cas Syndic
        SyndicSubscription subscription = syndicSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonnement introuvable"));

        User syndic = subscription.getSyndic();

        if (syndic.getStatus() != UserStatus.DISABLED) {
            throw new BadRequestException("Ce compte n'est pas suspendu");
        }

        syndic.setStatus(UserStatus.ACTIVE);
        userRepository.save(syndic);

        // Si la date de fin est déjà dépassée, le scheduler d'expiration horaire la rebasculera en EXPIRED tout seul
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        syndicSubscriptionRepository.save(subscription);

        if (notifyClient) {
            String subject = "Votre compte Solimus a été réactivé";
            String body = "Bonjour " + syndic.getFirstName() + ",\n\n" +
                    "Votre compte a été réactivé par un administrateur. Vous pouvez de nouveau vous connecter.\n\n" +
                    "Cordialement,\nL'équipe Solimus";
            emailService.sendEmail(syndic.getEmail(), subject, body);
        }
    }

    // =========================================================================
    // Renouvellement manuel de l'abonnement d'un abonné par l'admin — passe par TouchPay comme les autres flux
    // =========================================================================
    @Override
    @Transactional
    public SyndicPlanChangeResponseDTO renewSubscriber(Long subscriptionId, SubscriberType subscriberType,
                                                         AdminRenewSubscriptionDTO dto) {

        User currentAdmin = getCurrentAdmin();

        // Date de début du nouvel abonnement
        LocalDateTime startDate = dto.getStartDate().atStartOfDay();

        // Date de fin calculée selon la durée choisie
        LocalDateTime endDate = startDate.plusMonths(dto.getDuration().getMonths());

        // Cas où l'abonné est un prestataire
        if (subscriberType == SubscriberType.PRESTATAIRE) {

            // Récupère l'abonnement sélectionné
            ProviderSubscription current = providerSubscriptionRepository.findById(subscriptionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Abonnement introuvable"));

            // Récupère le prestataire concerné
            User provider = current.getProvider();

            // Empêche de lancer un second paiement si un renouvellement est déjà en attente
            if (providerSubscriptionRepository.existsByProviderIdAndStatus(provider.getId(), SubscriptionStatus.PENDING)) {
                throw new BadRequestException(
                        "Un paiement est déjà en attente de confirmation pour ce prestataire. Veuillez patienter avant de réessayer.");
            }

            // Récupère la nouvelle formule choisie
            ProviderPlan plan = providerPlanRepository.findById(dto.getPlanId())
                    .orElseThrow(() -> new ResourceNotFoundException("Formule d'abonnement introuvable"));

            // Vérifie que la formule est toujours disponible
            if (!Boolean.TRUE.equals(plan.getActive())) {
                throw new BadRequestException("Cette formule n'est plus disponible");
            }

            // Détermine le tarif selon la durée choisie
            BigDecimal amount = dto.getDuration() == SubscriptionDuration.YEARLY
                    ? plan.getYearlyPrice()
                    : plan.getMonthlyPrice();

            // Vérifie qu'un tarif existe pour cette durée
            if (amount == null) {
                throw new BadRequestException(
                        dto.getDuration() == SubscriptionDuration.YEARLY
                                ? "Cette formule ne propose pas de tarif annuel"
                                : "Cette formule ne propose pas de tarif mensuel");
            }

            // Référence préfixée PRA- (Prestataire Renouvelé par Admin)
            String transactionRef = generateReference("PRA");

            // Crée le nouvel abonnement en attente de paiement
            ProviderSubscription subscription = new ProviderSubscription();
            subscription.setProvider(provider);
            subscription.setInitiatedBy(currentAdmin);
            subscription.setProviderPlan(plan);
            subscription.setStatus(SubscriptionStatus.PENDING);
            subscription.setPaymentStatus(PaymentStatus.PENDING);
            subscription.setAmountPaid(amount);
            subscription.setMethod(dto.getMethod());
            subscription.setDuration(dto.getDuration());
            subscription.setTransactionRef(transactionRef);
            subscription.setStartDate(startDate);
            subscription.setEndDate(endDate);
            subscription.setNotifyClient(dto.isNotifyClient());
            subscription.setSendInvoiceEmail(dto.isSendInvoiceEmail());

            // Enregistre le renouvellement
            providerSubscriptionRepository.save(subscription);

            // Construit l'URL de paiement TouchPay
            String bridgeUrl = String.format(touchPayBridgeUrlTemplate, transactionRef);

            // Retourne les informations nécessaires pour finaliser le paiement
            return SyndicPlanChangeResponseDTO.builder()
                    .success(true)
                    .message("Renouvellement initié. Veuillez compléter le paiement via TouchPay.")
                    .transactionReference(transactionRef)
                    .amount(amount)
                    .paymentUrl(bridgeUrl)
                    .build();
        }

        //Cas Syndic
        SyndicSubscription current = syndicSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Abonnement introuvable"));
        User syndic = current.getSyndic();

        // Même garde-fou que le self-service : pas deux paiements en attente en même temps
        if (syndicSubscriptionRepository.existsBySyndicIdAndStatus(syndic.getId(), SubscriptionStatus.PENDING)) {
            throw new BadRequestException(
                    "Un paiement est déjà en attente de confirmation pour ce syndic. Veuillez patienter avant de réessayer.");
        }

        SyndicPlan plan = syndicPlanRepository.findById(dto.getPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Formule d'abonnement introuvable"));

        if (!Boolean.TRUE.equals(plan.getActive())) {
            throw new BadRequestException("Cette formule n'est plus disponible");
        }

        BigDecimal amount = dto.getDuration() == SubscriptionDuration.YEARLY
                ? plan.getYearlyPrice()
                : plan.getMonthlyPrice();

        if (amount == null) {
            throw new BadRequestException(
                    dto.getDuration() == SubscriptionDuration.YEARLY
                            ? "Cette formule ne propose pas de tarif annuel"
                            : "Cette formule ne propose pas de tarif mensuel");
        }

        // Référence préfixée SYA- (renouvellement par l'Admin), pour que le bridge et le callback la
        // distinguent de SYN- (création de compte) et SYR- (self-service)
        String transactionRef = generateReference("SYA");

        SyndicSubscription subscription = new SyndicSubscription();
        subscription.setSyndic(syndic);
        // Ici c'est l'admin qui paie, comme pour la création de compte
        subscription.setInitiatedBy(currentAdmin);
        subscription.setSyndicPlan(plan);
        subscription.setStatus(SubscriptionStatus.PENDING);
        subscription.setPaymentStatus(PaymentStatus.PENDING);
        subscription.setAmountPaid(amount);
        subscription.setMethod(dto.getMethod());
        subscription.setDuration(dto.getDuration());
        subscription.setTransactionRef(transactionRef);
        subscription.setStartDate(startDate);
        subscription.setEndDate(endDate);
        // Choix de l'admin, à respecter plus tard au callback TouchPay
        subscription.setNotifyClient(dto.isNotifyClient());
        subscription.setSendInvoiceEmail(dto.isSendInvoiceEmail());
        syndicSubscriptionRepository.save(subscription);

        String bridgeUrl = String.format(touchPayBridgeUrlTemplate, transactionRef);

        return SyndicPlanChangeResponseDTO.builder()
                .success(true)
                .message("Renouvellement initié. Veuillez compléter le paiement via TouchPay.")
                .transactionReference(transactionRef)
                .amount(amount)
                .paymentUrl(bridgeUrl)
                .build();
    }

    // ============================================================================
    // BLOC — MÉTHODES UTILITAIRES (communes, techniques)
    // ============================================================================

    // Récupère l'admin actuellement authentifié via le contexte de sécurité Spring
    private User getCurrentAdmin() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Administrateur introuvable"));
    }

    // Génère une référence courte et unique, préfixée selon le type de paiement (SYA-, PRA-...)
    private String generateReference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    // le statut du paiement
    private String mapPaymentStatusToLabel(PaymentStatus paymentStatus) {
        return switch (paymentStatus) {
            case PENDING -> "En attente";
            case COMPLETED -> "Payé";
            case FAILED -> "Échoué";
        };
    }

}

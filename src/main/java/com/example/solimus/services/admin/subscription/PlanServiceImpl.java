package com.example.solimus.services.admin.subscription;

import com.example.solimus.dtos.admin.subscription.*;
import com.example.solimus.entities.ProviderPlan;
import com.example.solimus.entities.SyndicPlan;
import com.example.solimus.enums.ProviderPlanFeature;
import com.example.solimus.enums.SyndicPlanFeature;
import com.example.solimus.enums.SubscriberType;
import com.example.solimus.enums.SubscriptionStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.ProviderPlanRepository;
import com.example.solimus.repositories.ProviderSubscriptionRepository;
import com.example.solimus.repositories.SubscriberRepository;
import com.example.solimus.repositories.SyndicPlanRepository;
import com.example.solimus.repositories.SyndicSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanServiceImpl implements PlanService {

    private final SyndicPlanRepository syndicPlanRepository;
    private final SyndicSubscriptionRepository syndicSubscriptionRepository;
    private final ProviderPlanRepository providerPlanRepository;
    private final ProviderSubscriptionRepository providerSubscriptionRepository;
    private final SubscriberRepository subscriberRepository;

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
        plan.setMaxCoOwners(dto.getMaxCoOwners());
        plan.setMaxUsers(dto.getMaxUsers());
        plan.setFeatures(dto.getFeatures() != null ? dto.getFeatures() : new HashSet<>());
        plan.setActive(dto.getActive() != null ? dto.getActive() : true);

        SyndicPlan saved = syndicPlanRepository.save(plan);

        return toDTO(saved);
    }

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
        if (dto.getMaxCoOwners() != null) {
            plan.setMaxCoOwners(dto.getMaxCoOwners());
        }
        if (dto.getMaxUsers() != null) {
            plan.setMaxUsers(dto.getMaxUsers());
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

    // ============================================================================
    // PARTIE — GESTION ABONNEMENT PRESTATAIRE
    // ============================================================================
    //
    // Save transparent : crée la formule si elle n'existe pas encore,
    // sinon met à jour la ligne existante avec les nouvelles valeurs
    // envoyées par l'admin depuis le formulaire.
    //
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


    // =========================================================================
    // Méthodes utilitaires
    // =========================================================================

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
                .maxCoOwners(plan.getMaxCoOwners())
                .maxUsers(plan.getMaxUsers())
                .features(featureDTOs)
                .active(plan.getActive())
                .subscribersCount(subscribersCount)
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }
}
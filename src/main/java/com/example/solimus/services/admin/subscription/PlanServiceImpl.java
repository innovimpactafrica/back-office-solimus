package com.example.solimus.services.admin.subscription;

import com.example.solimus.dtos.admin.subscription.PlanOverviewDTO;
import com.example.solimus.dtos.admin.subscription.SubscriptionKpiDTO;
import com.example.solimus.dtos.admin.subscription.SyndicPlanDTO;
import com.example.solimus.dtos.admin.subscription.SyndicPlanFeatureDTO;
import com.example.solimus.dtos.admin.subscription.SyndicPlanRequestDTO;
import com.example.solimus.entities.ProviderPlan;
import com.example.solimus.entities.SyndicPlan;
import com.example.solimus.enums.SyndicPlanFeature;
import com.example.solimus.repositories.ProviderPlanRepository;
import com.example.solimus.repositories.ProviderSubscriptionRepository;
import com.example.solimus.repositories.SyndicPlanRepository;
import com.example.solimus.repositories.SyndicSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    // =========================================================================
    // Création d'une nouvelle formule syndic
    // =========================================================================
    @Override
    @Transactional
    public SyndicPlanDTO createSyndicPlan(SyndicPlanRequestDTO dto) {

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
                    .planType("SYNDIC")
                    .name(plan.getName())
                    .monthlyPrice(plan.getMonthlyPrice())
                    .yearlyPrice(plan.getYearlyPrice())
                    .featureLabels(featureLabels)
                    .active(plan.getActive())
                    .subscribersCount(subscribersCount)
                    .build());
        }

        // Bloc 2 : l'unique formule prestataire (si elle existe déjà)
        var providerPlanOpt = providerPlanRepository.findFirstByOrderByIdAsc();
        if (providerPlanOpt.isPresent()) {

            ProviderPlan providerPlan = providerPlanOpt.get();
            //Compte le nombre de prestataires ayant un abonnement actif pour ce plan
            long providerSubscribersCount = providerSubscriptionRepository.countCurrentlyActive(LocalDateTime.now());
            allPlans.add(PlanOverviewDTO.builder()
                    .id(providerPlan.getId())
                    .planType("PRESTATAIRE")
                    .name(providerPlan.getName())
                    .monthlyPrice(providerPlan.getMonthlyPrice())
                    .yearlyPrice(providerPlan.getYearlyPrice())
                    .featureLabels(new ArrayList<>()) // ProviderPlan n'a pas de fonctionnalités à cocher
                    .active(true) // pas de champ active sur ProviderPlan, toujours actif par definition
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
    // Méthodes utilitaires
    // =========================================================================

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
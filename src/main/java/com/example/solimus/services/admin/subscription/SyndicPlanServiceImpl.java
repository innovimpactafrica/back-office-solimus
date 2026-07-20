package com.example.solimus.services.admin.subscription;

import com.example.solimus.dtos.admin.subscription.SyndicPlanDTO;
import com.example.solimus.dtos.admin.subscription.SyndicPlanFeatureDTO;
import com.example.solimus.dtos.admin.subscription.SyndicPlanRequestDTO;
import com.example.solimus.entities.SyndicPlan;
import com.example.solimus.enums.SyndicPlanFeature;
import com.example.solimus.repositories.SyndicPlanRepository;
import com.example.solimus.repositories.SyndicSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SyndicPlanServiceImpl implements SyndicPlanService {

    private final SyndicPlanRepository syndicPlanRepository;
    private final SyndicSubscriptionRepository syndicSubscriptionRepository;

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
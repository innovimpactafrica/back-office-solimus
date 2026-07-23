package com.example.solimus.services.admin.subscription;

import com.example.solimus.dtos.admin.subscription.*;
import com.example.solimus.enums.SubscriberType;
import com.example.solimus.enums.SubscriptionStatus;

import java.util.List;

public interface PlanService {

    // ===== Formules Syndic =====
    SyndicPlanDTO createSyndicPlan(SyndicPlanRequestDTO dto);
    SyndicPlanDTO updateSyndicPlan(Long id, SyndicPlanRequestDTO dto);
    SyndicPlanDTO toggleSyndicPlanStatus(Long id, boolean active);
    void deleteSyndicPlan(Long id);

    // ===== Formules Prestataire =====
    ProviderPlanDTO createProviderPlan(ProviderPlanRequestDTO dto);
    ProviderPlanDTO updateProviderPlan(Long id, ProviderPlanRequestDTO dto);
    ProviderPlanDTO toggleProviderPlanStatus(Long id, boolean active);
    void deleteProviderPlan(Long id);

    // ===== Listing unifié + KPIs =====
    List<PlanOverviewDTO> getAllPlansOverview();
    SubscriptionKpiDTO getSubscriptionKpis();

    // ===== Liste unifiée des abonnés (Syndic + Prestataire) =====
    SubscriberListResponseDTO getAllSubscribers(String search, SubscriptionStatus status,
                                                 SubscriberType subscriberType, int page, int size);
}
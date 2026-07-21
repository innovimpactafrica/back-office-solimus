package com.example.solimus.services.admin.subscription;

import com.example.solimus.dtos.admin.subscription.PlanOverviewDTO;
import com.example.solimus.dtos.admin.subscription.SubscriptionKpiDTO;
import com.example.solimus.dtos.admin.subscription.SyndicPlanDTO;
import com.example.solimus.dtos.admin.subscription.SyndicPlanRequestDTO;

import java.util.List;

public interface PlanService {

    // Création d'une nouvelle formule syndic
    SyndicPlanDTO createSyndicPlan(SyndicPlanRequestDTO dto);

    //Listing
    List<PlanOverviewDTO> getAllPlansOverview();

    // KPIs de la page Gestion des abonnements
    SubscriptionKpiDTO getSubscriptionKpis();
}
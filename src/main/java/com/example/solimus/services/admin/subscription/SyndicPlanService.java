package com.example.solimus.services.admin.subscription;

import com.example.solimus.dtos.admin.subscription.SyndicPlanDTO;
import com.example.solimus.dtos.admin.subscription.SyndicPlanRequestDTO;

public interface SyndicPlanService {

    // Création d'une nouvelle formule syndic
    SyndicPlanDTO createSyndicPlan(SyndicPlanRequestDTO dto);
}
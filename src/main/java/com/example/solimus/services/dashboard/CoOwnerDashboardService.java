package com.example.solimus.services.dashboard;

import com.example.solimus.dtos.dashboard.CoOwnerDashboardDTO;

public interface CoOwnerDashboardService {

    /**
     * Récupère les données du dashboard du copropriétaire.
     *
     * @param propertyId ID du bien sélectionné (optionnel, prend le premier par défaut)
     * @return données du dashboard
     */
    CoOwnerDashboardDTO getDashboard(Long propertyId);
}

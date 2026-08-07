package com.example.solimus.services.tenant.accueil;

import com.example.solimus.dtos.tenant.accueil.TenantDashboardDTO;

public interface TenantAccueilService {

    /**
     * Dashboard d'accueil du locataire connecté : infos du bien loué, compteurs de signalements
     * par statut, et les travaux en cours ou planifiés (limité aux 3 plus récents).
     */
    TenantDashboardDTO getDashboard();
}

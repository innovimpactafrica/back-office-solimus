package com.example.solimus.services.syndic.travaux;

import com.example.solimus.dtos.syndic.travaux.*;
import com.example.solimus.enums.InterventionStatus;

import java.util.List;

public interface SyndicTravauxDashboardService {

    /**
     * Retourne les 6 KPIs du dashboard "Gestion des demandes travaux".
     */
    TravauxDashboardDTO getDashboard();

    /**
     * Liste paginée des incidents travaux du syndic, avec recherche et filtres.
     */
    SyndicTravauxListResponse getIncidents(String search, InterventionStatus status, Long residenceId, int page, int size);

    /**
     * Vue générale d'un incident (onglet 1).
     */
    SyndicTravauxDetailDTO getVueGenerale(Long id);

    /**
     * Liste des devis reçus pour un incident (onglet 2).
     */
    List<SyndicQuoteCardDTO> getQuotes(Long id);

    /**
     * Détail d'un devis précis (onglet 2 → clic).
     */
    SyndicQuoteDetailDTO getQuoteDetail(Long id, Long quoteId);

    /**
     * Données de l'onglet Intervention (onglet 3).
     */
    SyndicInterventionTabDTO getInterventionTab(Long id);

    /**
     * Historique complet (onglet 4).
     */
    List<SyndicHistoryItemDTO> getHistory(Long id);
}
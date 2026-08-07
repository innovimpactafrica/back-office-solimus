package com.example.solimus.services.syndic.travaux;

import com.example.solimus.dtos.syndic.travaux.SyndicHistoryItemDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicTravauxDetailDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicTravauxListResponse;
import com.example.solimus.dtos.syndic.travaux.TravauxDashboardDTO;
import com.example.solimus.enums.InterventionStatus;

import java.util.List;

/**
 * Flux manuel de gestion des travaux — remplace le workflow devis/prestataire pour toutes les
 * interventions (copropriétaire, locataire, ou converties depuis un signalement) tant que le
 * circuit prestataire digitalisé reste désactivé. Réutilise les mêmes DTOs que
 * {@link SyndicTravauxDashboardService} — seuls les champs liés au prestataire restent vides.
 */
public interface SyndicTravauxManualService {

    /**
     * Retourne les 6 KPIs du dashboard "Gestion des demandes travaux".
     */
    TravauxDashboardDTO getDashboard();

    /**
     * Liste paginée des incidents travaux du syndic, avec recherche et filtres.
     */
    SyndicTravauxListResponse getIncidents(String search, InterventionStatus status, Long residenceId, int page, int size);

    /**
     * Vue générale d'un incident (infos + participants, sans les champs prestataire).
     */
    SyndicTravauxDetailDTO getVueGenerale(Long id);

    /**
     * Historique complet des changements de statut d'un incident.
     */
    List<SyndicHistoryItemDTO> getHistory(Long id);

    /**
     * Passe la demande de PENDING à STARTED — prise en charge par le syndic.
     */
    void markAsInProgress(Long id, String note);

    /**
     * Passe la demande de STARTED à FINISHED — travaux terminés.
     */
    void markAsFinished(Long id, String note);

    /**
     * Passe la demande de FINISHED à FINAL_VALIDATION — clôture définitive.
     */
    void closeIntervention(Long id, String closingNote);
}

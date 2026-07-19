package com.example.solimus.services.syndic.dashboard;

import com.example.solimus.dtos.syndic.dashboard.*;
import com.example.solimus.dtos.syndic.travaux.SyndicResidenceDTO;

import java.util.List;

public interface DashboardService {

    /**
     * Retourne les 6 KPIs principaux du tableau de bord.
     * residenceId est OPTIONNEL : si fourni, filtre sur cette résidence ;
     * si absent, utilise automatiquement la résidence la plus récemment créée par le syndic.
     */
    MainDashboardDTO getMainDashboard(Long residenceId);

    /**
     * Retourne les alertes importantes du syndic, toutes résidences confondues.
     */
    List<AlertDTO> getImportantAlerts();

    /**
     * Retourne les dernières activités du syndic, toutes résidences confondues.
     */
    List<ActivityRowDTO> getRecentActivities(int limit);

    /**
     * Retourne les derniers incidents gérés par le syndic, toutes résidences confondues.
     */
    List<RecentIncidentDTO> getRecentIncidents(int limit);

    /**
     * Retourne la liste des résidences du syndic connecté (id + nom uniquement),
     * pour peupler le dropdown de sélection de résidence.
     */
    List<SyndicResidenceDTO> getMyResidencesForDropdown();
}
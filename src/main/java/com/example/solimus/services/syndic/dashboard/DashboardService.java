package com.example.solimus.services.syndic.dashboard;

import com.example.solimus.dtos.syndic.dashboard.*;

import java.util.List;

public interface DashboardService {

    /**
     * Retourne les 6 KPIs principaux du tableau de bord, filtrés sur une résidence précise
     * (Trésorerie, Taux de recouvrement, Impayés, Résidences gérées, Incidents ouverts, Interventions du jour).
     */
    MainDashboardDTO getMainDashboard(Long residenceId);

    /**
     * Retourne le graphique "Évolution Financière" (Trésorerie vs Appels de charges cumulés),
     * filtré sur une résidence précise, sur les 6 premiers mois de l'année en cours.
     */
    List<TreasuryEvolutionPointDTO> getFinancialEvolution(Long residenceId);

    /**
     * Retourne les alertes importantes du syndic (impayés significatifs + AG à préparer),
     * toutes résidences confondues.
     */
    List<AlertDTO> getImportantAlerts();

    /**
     * Retourne les dernières activités du syndic (journal d'activité), toutes résidences confondues.
     */
    List<ActivityRowDTO> getRecentActivities(int limit);

    /**
     * Retourne les derniers incidents gérés par le syndic (managementMode = SYNDIC),
     * toutes résidences confondues.
     */
    List<RecentIncidentDTO> getRecentIncidents(int limit);
}
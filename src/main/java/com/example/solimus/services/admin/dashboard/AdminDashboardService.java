package com.example.solimus.services.admin.dashboard;

import com.example.solimus.dtos.admin.dashboard.*;

import java.util.List;

public interface AdminDashboardService {

    // ===== Bloc 1 =====

    /**
     * KPIs du dashboard admin global : syndics/prestataires actifs, résidences, copropriétaires,
     * abonnements actifs/expirés, revenus du mois et annuels (Syndic + Prestataire confondus).
     */
    AdminDashboardKpiDTO getDashboardKpis();

    // ===== Bloc 2 =====

    /**
     * Revenus des abonnements (Syndic + Prestataire) mois par mois, pour l'année demandée
     * (année en cours si non précisée). Retourne toujours 12 éléments, un par mois.
     */
    List<MonthlyRevenueDTO> getMonthlyRevenue(Integer year);

    // ===== Bloc 3 =====

    /**
     * Les N dernières activités importantes de la plateforme, tous modules confondus (nouveaux
     * syndics, prestataires inscrits, renouvellements, paiements, nouvelles résidences, nouveaux
     * copropriétaires), triées de la plus récente à la plus ancienne.
     */
    List<PlatformActivityRowDTO> getRecentActivities(int limit);

    // ===== Bloc 4 =====

    /**
     * Répartition des utilisateurs de la plateforme entre Syndics, Prestataires et Copropriétaires,
     * avec le nombre et le pourcentage de chacun.
     */
    UserBreakdownDTO getUserBreakdown();

    // ===== Bloc 5 =====

    /**
     * Les N derniers syndics enregistrés sur la plateforme, du plus récent au plus ancien.
     */
    List<RecentSyndicDTO> getRecentSyndics(int limit);
}

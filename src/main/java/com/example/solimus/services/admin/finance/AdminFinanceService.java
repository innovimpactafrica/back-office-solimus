package com.example.solimus.services.admin.finance;

import com.example.solimus.dtos.admin.finance.*;
import com.example.solimus.enums.PaymentMethod;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.RevenuePeriod;
import com.example.solimus.enums.SubscriberType;
import org.springframework.data.domain.Page;

import java.util.List;

public interface AdminFinanceService {

    // ===== Bloc 1 =====

    /**
     * KPIs de la page "Finances" : revenus du mois/année, part Syndic/Prestataire, taux de
     * croissance trimestriel, paiements en attente, renouvellements à venir.
     */
    FinanceDashboardKpiDTO getDashboardKpis();

    // ===== Bloc 2 =====

    /**
     * Points du graphique "Évolution des Revenus", pour la granularité demandée (mensuelle,
     * trimestrielle ou annuelle). "year" est ignoré pour la granularité annuelle.
     */
    List<RevenueChartPointDTO> getRevenueChart(RevenuePeriod period, Integer year);

    // ===== Bloc 3 =====

    /**
     * Répartition des revenus du mois en cours entre Syndics et Prestataires.
     */
    RevenueSplitDTO getRevenueSplit();

    // ===== Bloc 4 =====

    /**
     * Revenu cumulé total (depuis toujours) et nombre d'abonnés actifs, pour chaque formule
     * existante (Syndic + Prestataire confondus, liste plate).
     */
    List<PlanRevenueDTO> getRevenueByPlan();

    // ===== Bloc 5 =====

    /**
     * Liste paginée des transactions d'abonnements (Syndic + Prestataire), triées de la plus
     * récente à la plus ancienne, avec filtres optionnels.
     */
    Page<TransactionRowDTO> getTransactions(SubscriberType type, String planName, PaymentStatus status,
                                             int page, int size);
}

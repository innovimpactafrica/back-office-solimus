package com.example.solimus.services.admin.provider;

import com.example.solimus.dtos.admin.provider.*;
import com.example.solimus.enums.SubscriptionStatus;

import java.util.List;

public interface AdminProviderService {

    // ===== Dashboard =====

    /**
     * KPIs de la page "Gestion des prestataires" (total, actifs, suspendus, abonnements expirant
     * bientôt, revenus totaux des abonnements prestataire).
     */
    AdminProviderDashboardKpiDTO getDashboardKpis();

    // ===== Liste des prestataires =====

    /**
     * Liste paginée des prestataires, avec recherche (entreprise, responsable, email) et filtre par
     * statut d'abonnement.
     */
    AdminProviderListResponseDTO getAllProviders(String search, SubscriptionStatus status, int page, int size);

    // ===== Détail d'un prestataire =====

    /**
     * Fiche détail complète d'un prestataire : en-tête, KPIs, informations générales, zones
     * d'intervention, abonnement en cours (Blocs A à E).
     */
    ProviderDetailDTO getProviderDetail(Long providerId);

    /**
     * Historique d'activité assemblé (Bloc F) : compte créé, abonnements souscrits/renouvelés,
     * missions terminées, avis reçus — triés du plus récent au plus ancien.
     */
    List<ProviderActivityRowDTO> getProviderRecentActivities(Long providerId, int limit);
}

package com.example.solimus.services.admin.syndic;

import com.example.solimus.dtos.admin.syndic.*;
import com.example.solimus.dtos.syndic.dashboard.ActivityRowDTO;
import com.example.solimus.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;

import java.util.List;

public interface SyndicService {

    // ===== Création d'un syndic =====

    /**
     * Crée un compte syndic (User + SyndicProfile + SyndicSubscription en attente de paiement).
     * Le mot de passe temporaire n'est généré et envoyé par email qu'à la confirmation du paiement.
     */
    CreateSyndicResponseDTO createSyndic(CreateSyndicDTO dto);

    /**
     * Retourne les formules actives (id + nom), pour la liste déroulante du formulaire "Nouveau syndic".
     */
    List<SyndicPlanOptionDTO> listAvailablePlans();

    // ===== Dashboard =====

    /**
     * KPIs de la page "Gestion des syndics" (total, actifs, suspendus, abonnements expirant bientôt,
     * total des résidences gérées, toutes syndics confondus).
     */
    AdminSyndicDashboardKpiDTO getDashboardKpis();

    // ===== Liste des syndics =====

    /**
     * Liste paginée des syndics, avec recherche (nom, prénom, email, entreprise) et filtre par statut
     * d'abonnement.
     */
    AdminSyndicListResponseDTO getAllSyndics(String search, SubscriptionStatus status, int page, int size);

    // ===== Détail d'un syndic =====

    /**
     * Fiche détail complète d'un syndic : informations générales, KPIs, abonnement en cours.
     */
    SyndicDetailDTO getSyndicDetail(Long syndicId);

    // ===== Résidences gérées par un syndic =====

    /**
     * Liste paginée des résidences gérées par ce syndic, avec le nombre d'alertes de chacune
     * (AG à venir / paiements en retard / intervention urgente).
     */
    Page<SyndicManagedResidenceDTO> getSyndicResidences(Long syndicId, int page, int size);

    /**
     * Fiche détail d'une résidence précise gérée par ce syndic : informations générales + KPIs.
     */
    ResidenceDetailDTO getResidenceDetail(Long syndicId, Long residenceId);

    /**
     * Répartition paginée des biens de cette résidence par type (T1, T2, T3...).
     */
    Page<PropertyTypeBreakdownDTO> getResidencePropertyTypeBreakdown(Long syndicId, Long residenceId, int page, int size);

    /**
     * Les N derniers incidents (signalements) de cette résidence, du plus récent au plus ancien.
     */
    List<ResidenceIncidentRowDTO> getResidenceRecentIncidents(Long syndicId, Long residenceId, int limit);

    // ===== Activités récentes =====

    /**
     * Les N dernières activités de ce syndic, toutes résidences confondues (vues par l'admin).
     */
    List<ActivityRowDTO> getSyndicRecentActivities(Long syndicId, int limit);
}

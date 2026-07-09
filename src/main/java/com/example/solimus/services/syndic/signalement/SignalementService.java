package com.example.solimus.services.syndic.signalement;

import com.example.solimus.dtos.syndic.signalement.*;
import com.example.solimus.enums.SignalementStatus;

public interface SignalementService {

    /**
     * Retourne les 4 KPIs du dashboard "Gestion des signalements" du syndic connecté.
     */
    SignalementDashboardDTO getDashboard();

    /**
     * Liste paginée des signalements du syndic connecté, avec recherche et filtres.
     */
    SyndicSignalementListResponse getSignalementsForSyndic(
            String search, SignalementStatus status, Long residenceId, int page, int size);

    /**
     * Détail complet d'un signalement, vue syndic.
     */
    SyndicSignalementDetailDTO getSignalementDetailForSyndic(Long id);

    /**
     * Résout un signalement sans transformer en travaux : ajoute une note de clôture,
     * passe le statut à RESOLVED, notifie le copropriétaire.
     */
    void resolveWithoutWork(Long id, ResolveSignalementDTO dto);

    /**
     * Transforme un signalement en demande de travaux : crée une InterventionRequest liée,
     * passe le statut du signalement à CONVERTED_TO_WORK.
     */
    Long convertToWork(Long id, ConvertToWorkDTO dto);
}
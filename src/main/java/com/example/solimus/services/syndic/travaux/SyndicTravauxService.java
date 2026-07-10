package com.example.solimus.services.syndic.travaux;

import com.example.solimus.dtos.syndic.residence.CommonFacilityDTO;
import com.example.solimus.dtos.syndic.residence.PropertyDTO;
import com.example.solimus.dtos.syndic.settings.SpecialtyDTO;
import com.example.solimus.dtos.syndic.travaux.CreateInterventionRequestDTO;
import com.example.solimus.dtos.syndic.travaux.CreateReviewDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicResidenceDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicDepositSummaryDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicPayDepositDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicBalancePaymentSummaryDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicPaymentResultDTO;
import com.example.solimus.dtos.syndic.travaux.UpdateInterventionRequestDTO;

import java.util.List;

public interface SyndicTravauxService {

    // =========================================================================
    // CRÉATION D'INTERVENTION
    // =========================================================================
    void createInterventionRequest(CreateInterventionRequestDTO dto);

    // =========================================================================
    // ENVOI AUX PRESTATAIRES
    // =========================================================================
    /**
     * Envoie une demande de partie commune créée par un owner aux prestataires proches.
     * Le syndic doit valider et diffuser la demande aux prestataires.
     */
    void sendToProviders(Long interventionId);

    // =========================================================================
    // LISTER LES RÉSIDENCES DU SYNDIC
    // =========================================================================
    /**
     * Lister toutes les résidences créées par le syndic connecté.
     */
    List<SyndicResidenceDTO> getMesResidences();

    // =========================================================================
    // LISTER LES LOTS D'UNE RÉSIDENCE
    // =========================================================================
    /**
     * Lister tous les lots d'une résidence spécifique.
     * Vérifie que la résidence appartient au syndic connecté.
     */
    List<PropertyDTO> getPropertiesByResidence(Long residenceId);

    // =========================================================================
    // LISTER LES BIENS COMMUNS D'UNE RÉSIDENCE
    // =========================================================================
    /**
     * Lister les biens communs d'une résidence.
     * Vérifie que la résidence appartient au syndic connecté.
     */
    List<CommonFacilityDTO> getCommonFacilitiesByResidence(Long residenceId);

    // =========================================================================
    // LISTER LES SPÉCIALITÉS
    // =========================================================================
    /**
     * Lister toutes les spécialités disponibles pour la création d'intervention.
     */
    List<SpecialtyDTO> getAllSpecialties();

    // =========================================================================
    // CRÉATION D'AVIS
    // =========================================================================
    /**
     * Créer un avis pour une intervention terminée.
     */
    void createReview(Long interventionId, CreateReviewDTO dto);

    // =========================================================================
    // VALIDATION DE DEVIS ET PAIEMENTS (partie commune, géré par le syndic)
    // =========================================================================

    /**
     * Valide un devis pour une intervention gérée par le syndic (partie commune).
     * Rejette automatiquement les autres devis concurrents.
     */
    void validateQuote(Long interventionId, Long quoteId);

    /**
     * Retourne le récapitulatif à afficher dans le modal "Acompte" après validation du devis.
     */
    SyndicDepositSummaryDTO getDepositSummary(Long interventionId);

    /**
     * Verse un acompte au prestataire depuis le wallet du syndic.
     */
    SyndicPaymentResultDTO payDeposit(Long interventionId, SyndicPayDepositDTO dto);

    /**
     * Retourne le récapitulatif à afficher dans le modal "Paiement" (solde final).
     */
    SyndicBalancePaymentSummaryDTO getBalanceSummary(Long interventionId);

    /**
     * Paie le solde restant depuis le wallet du syndic et clôture l'intervention (FINAL_VALIDATION).
     */
    SyndicPaymentResultDTO payBalanceAndClose(Long interventionId);

    // =========================================================================
    // MISE À JOUR ET SUPPRESSION D'INTERVENTION
    // =========================================================================

    /**
     * Met à jour partiellement une demande d'intervention (seuls les champs fournis sont modifiés).
     * Uniquement possible si l'intervention est en attente de devis (PENDING).
     */
    void updateIntervention(Long interventionId, UpdateInterventionRequestDTO dto);

    /**
     * Supprime une demande d'intervention.
     * Uniquement possible si l'intervention est en attente de devis (PENDING).
     */
    void deleteIntervention(Long interventionId);

}

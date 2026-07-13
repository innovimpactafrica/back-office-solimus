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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
    Page<SyndicResidenceDTO> getMesResidences(Integer page, Integer size);

    // =========================================================================
    // LISTER LES LOTS D'UNE RÉSIDENCE
    // =========================================================================
    /**
     * Lister tous les lots d'une résidence spécifique.
     * Vérifie que la résidence appartient au syndic connecté.
     */
    Page<PropertyDTO> getPropertiesByResidence(Long residenceId, Integer page, Integer size);

    // =========================================================================
    // LISTER LES BIENS COMMUNS D'UNE RÉSIDENCE
    // =========================================================================
    /**
     * Lister les biens communs d'une résidence.
     * Vérifie que la résidence appartient au syndic connecté.
     */
    Page<CommonFacilityDTO> getCommonFacilitiesByResidence(Long residenceId, Integer page, Integer size);

    // =========================================================================
    // LISTER LES SPÉCIALITÉS
    // =========================================================================
    /**
     * Lister toutes les spécialités disponibles pour la création d'intervention.
     */
    Page<SpecialtyDTO> getAllSpecialties(Integer page, Integer size);

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
     * Paie le solde restant (via Wallet SOLIMUS ou Mobile Money selon le DTO) et clôture l'intervention.
     * Wallet : débit synchrone + clôture immédiate. Mobile Money : initie TouchPay, clôture au callback.
     */
    SyndicPaymentResultDTO payBalanceAndClose(Long interventionId, SyndicPayDepositDTO dto);

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

    /**
     * Ajoute des photos à une intervention existante.
     */
    void addPhotosToIntervention(Long interventionId, List<String> photoUrls);

}

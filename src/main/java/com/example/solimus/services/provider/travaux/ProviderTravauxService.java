package com.example.solimus.services.provider.travaux;

import com.example.solimus.dtos.provider.travaux.ProviderTravauxDetailDTO;
import com.example.solimus.dtos.provider.travaux.ProviderTravauxPageDTO;
import com.example.solimus.enums.InterventionStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Service de l'onglet "Travaux" du prestataire.
 * Liste toutes les demandes dont le devis du prestataire a été accepté
 * (travaux acceptés, en cours, terminés, clôturés).
 */
public interface ProviderTravauxService {

    /**
     * Lister les travaux du prestataire connecté (recherche + filtre statut + pagination).
     */
    ProviderTravauxPageDTO getMyWorks(String search, InterventionStatus status, int page, int size);

    /**
     * Voir le détail d'une intervention assignée.
     */
    ProviderTravauxDetailDTO getWorkDetails(Long id);

    /**
     * Démarrer une intervention (passer de QUOTE_VALIDATED à STARTED).
     */
    void startIntervention(Long requestId);

    /**
     * Terminer une intervention (passer de STARTED à FINISHED).
     */
    void finishIntervention(Long requestId, String commentaire, List<MultipartFile> photos);
}

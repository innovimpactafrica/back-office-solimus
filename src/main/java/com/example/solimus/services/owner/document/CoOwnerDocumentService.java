package com.example.solimus.services.owner.document;

import com.example.solimus.dtos.document.CoOwnerDocumentDTO;
import com.example.solimus.dtos.document.DocumentDownloadUrlDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CoOwnerDocumentService {

    /**
     * Récupère tous les documents du copropriétaire connecté (réunions + charges).
     * Supporte la recherche, le filtre par type et la pagination.
     *
     * @param search terme de recherche optionnel (dans le nom du fichier)
     * @param documentType filtre optionnel par type de document
     * @param source filtre optionnel par source ("MEETING" ou "CHARGE")
     * @param pageable paramètres de pagination
     * @return page de documents
     */
    Page<CoOwnerDocumentDTO> getMesDocuments(String search, String documentType, String source, Pageable pageable);

    DocumentDownloadUrlDTO getDownloadUrl(String source, Long sourceId, String fileName);
}

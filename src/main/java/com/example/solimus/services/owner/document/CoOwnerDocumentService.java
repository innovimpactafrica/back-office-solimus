package com.example.solimus.services.owner.document;


import com.example.solimus.dtos.owner.document.CoOwnerDocumentListResponseDTO;

public interface CoOwnerDocumentService {

    /**
     * Récupère tous les documents du copropriétaire connecté (réunions + charges).
     * Supporte la recherche, le filtre par type et la pagination.
     */

    CoOwnerDocumentListResponseDTO getMyDocuments(String search, String category, int page, int size);

}

package com.example.solimus.controllers;

import com.example.solimus.dtos.document.CoOwnerDocumentDTO;
import com.example.solimus.services.document.CoOwnerDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coowner/documents")
@RequiredArgsConstructor
@Tag(name = "3.e Copropriétaire - Documents", description = "Tous les documents du copropriétaire : PV de réunion, convocations, factures de charges, rapports.")
public class OwnerDocumentController {

    private final CoOwnerDocumentService documentService;

    @Operation(summary = "Liste de tous mes documents", description = "Agrège les documents des réunions et des charges. Triés par date décroissante.")
    @GetMapping
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<Page<CoOwnerDocumentDTO>> getMesDocuments(
            @Parameter(description = "Terme de recherche dans le nom du fichier")
            @RequestParam(required = false) String search,
            @Parameter(description = "Filtre par type de document")
            @RequestParam(required = false) String documentType,
            @Parameter(description = "Filtre par source (MEETING ou CHARGE)")
            @RequestParam(required = false) String source,
            @Parameter(description = "Numéro de page (défaut: 0)")
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "Taille de la page (défaut: 5)")
            @RequestParam(required = false, defaultValue = "5") Integer size,
            @Parameter(description = "Tri (défaut: date,desc)")
            @RequestParam(required = false, defaultValue = "date,desc") String sort) {

        // Créer le Pageable avec les valeurs par défaut
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "date"));

        return ResponseEntity.ok(documentService.getMesDocuments(search, documentType, source, pageable));
    }
}

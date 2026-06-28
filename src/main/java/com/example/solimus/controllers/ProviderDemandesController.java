package com.example.solimus.controllers;

import com.example.solimus.dtos.admin.EstimatedDelayDTO;
import com.example.solimus.dtos.provider.request.CreateQuoteDTO;
import com.example.solimus.dtos.intervention.InterventionRequestDTO;
import com.example.solimus.dtos.provider.request.ProviderQuoteListDTO;
import com.example.solimus.dtos.provider.request.QuoteDetailDTO;
import com.example.solimus.dtos.provider.request.ProviderRequestsPageDTO;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.QuoteStatus;
import com.example.solimus.services.provider.ProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/provider/demandes")
@RequiredArgsConstructor
@Tag(name = "5.b Prestataire - Demandes", description = "Gestion des demandes d'intervention et des devis")
public class ProviderDemandesController {

    private final ProviderService providerService;

    // ==================== LISTE DES DEMANDES ====================

    @Operation(summary = "Lister les demandes d'intervention (Listing paginé et filtré)")
    @GetMapping("/available-requests")
    public ResponseEntity<ProviderRequestsPageDTO> getAvailableRequests(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) InterventionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(providerService.getAvailableRequests(search, status, PageRequest.of(page, size)));
    }

    @Operation(summary = "Obtenir le nombre total de demandes reçues")
    @GetMapping("/requests/count")
    public ResponseEntity<java.util.Map<String, Long>> getTotalRequestsCount() {
        return ResponseEntity.ok(java.util.Map.of("total", providerService.getTotalRequestsCount()));
    }

    @Operation(summary = "Récupérer le détail complet d'une demande par son ID")
    @GetMapping("/requests/{id}")
    public ResponseEntity<InterventionRequestDTO> getRequestDetails(@PathVariable Long id) {
        return ResponseEntity.ok(providerService.getRequestDetails(id));
    }

    @Operation(summary = "Lister mes interventions assignées")
    @GetMapping("/my-interventions")
    public ResponseEntity<Page<InterventionRequestDTO>> getMyInterventions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) InterventionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(providerService.getMyInterventions(search, status, page, size));
    }

    // ==================== GESTION DES DEVIS ====================

    @Operation(summary = "Créer un devis (Brouillon ou Envoi)")
    @PostMapping("/quotes")
    public ResponseEntity<String> createQuote(@RequestBody @Valid CreateQuoteDTO dto) {
        providerService.createQuote(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body("Devis enregistré avec succès.");
    }

    @Operation(summary = "Lister les délais d'estimation disponibles")
    @GetMapping("/estimated-delays")
    public ResponseEntity<List<EstimatedDelayDTO>> getEstimatedDelays() {
        return ResponseEntity.ok(providerService.getEstimatedDelays());
    }

    @Operation(summary = "Récupérer la liste des devis (avec filtres et pagination)")
    @GetMapping("/quotes")
    public ResponseEntity<ProviderQuoteListDTO> getMesDevis(
            @RequestParam(required = false) QuoteStatus statut,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(providerService.getMesDevis(statut, search, page, size));
    }

    @Operation(summary = "Récupérer le détail d'un devis")
    @GetMapping("/quotes/{id}")
    public ResponseEntity<QuoteDetailDTO> getQuoteDetails(@PathVariable Long id) {
        return ResponseEntity.ok(providerService.getQuoteDetails(id));
    }

    // ==================== EXÉCUTION DES INTERVENTIONS ====================

    @Operation(summary = "Démarrer une intervention (Prestataire)")
    @PostMapping("/requests/{id}/start")
    public ResponseEntity<String> startIntervention(@PathVariable Long id) {
        providerService.startIntervention(id);
        return ResponseEntity.ok("Les travaux ont été démarrés avec succès.");
    }

    @Operation(summary = "Ajouter une photo pendant les travaux")
    @PostMapping(value = "/requests/{id}/work-photos", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> ajouterPhotoTravaux(
            @PathVariable Long id,
            @RequestParam("photo") MultipartFile photo) {
        providerService.ajouterPhotoTravaux(id, photo);
        return ResponseEntity.ok("Photo ajoutée avec succès.");
    }

    @Operation(summary = "Ajouter un commentaire sur l'intervention")
    @PostMapping("/requests/{id}/comments")
    public ResponseEntity<String> ajouterCommentaire(
            @PathVariable Long id,
            @RequestParam("commentaire") String commentaire) {
        providerService.ajouterCommentaire(id, commentaire);
        return ResponseEntity.ok("Commentaire ajouté avec succès.");
    }

    @Operation(summary = "Terminer l'intervention")
    @PostMapping("/requests/{id}/finish")
    public ResponseEntity<String> terminerIntervention(@PathVariable Long id) {
        providerService.terminerIntervention(id);
        return ResponseEntity.ok("L'intervention a été marquée comme terminée.");
    }
}

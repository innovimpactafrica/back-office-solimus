package com.example.solimus.controllers;

import com.example.solimus.dtos.admin.EstimatedDelayDTO;
import com.example.solimus.dtos.provider.request.CreateQuoteDTO;
import com.example.solimus.dtos.provider.request.ProviderRequestDetailDTO;
import com.example.solimus.dtos.provider.request.ProviderRequestsDTO;
import com.example.solimus.enums.ProviderRequestDisplayStatus;
import com.example.solimus.services.provider.request.ProviderRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/provider/requests")
@RequiredArgsConstructor
@Tag(name = "Prestataire - Demandes", description = "Demandes de travaux notifiées au prestataire")
public class ProviderRequestController {

    private final ProviderRequestService providerRequestService;

    @Operation(summary = "Lister mes demandes de travaux (notifiées, non assignées)")
    @PreAuthorize("hasRole('ROLE_PROVIDER')")
    @GetMapping
    public ResponseEntity<ProviderRequestsDTO> getAvailableRequests(

            // Filtre optionnel par statut affiché — si absent, retourne tout
            @RequestParam(required = false) ProviderRequestDisplayStatus status,

            // Recherche par titre de la demande ou nom de la résidence — si absent, pas de filtre
            @RequestParam(required = false) String search,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        // On trie par date de création, la plus récente en premier
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        ProviderRequestsDTO result = providerRequestService
                .getAvailableRequests(status, search, pageable);

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Voir les détails d'une demande de travaux")
    @PreAuthorize("hasRole('ROLE_PROVIDER')")
    @GetMapping("/{id}")
    public ResponseEntity<ProviderRequestDetailDTO> getRequestDetails(@PathVariable Long id) {
        return ResponseEntity.ok(providerRequestService.getRequestDetails(id));
    }

    @Operation(summary = "Créer un devis (Brouillon ou Envoi)")
    @PostMapping("/quote")
    @PreAuthorize("hasRole('ROLE_PRESTATAIRE')")
    public ResponseEntity<String> createQuote(@RequestBody @Valid CreateQuoteDTO dto) {
        providerRequestService.createQuote(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body("Devis enregistré avec succès.");
    }

    @Operation(summary = "Lister les délais d'estimation disponibles")
    @GetMapping("quote/estimated-delays")
    @PreAuthorize("hasRole('ROLE_PRESTATAIRE')")
    public ResponseEntity<List<EstimatedDelayDTO>> getEstimatedDelays() {
        return ResponseEntity.ok(providerRequestService.getEstimatedDelays());
    }
}

package com.example.solimus.controllers;

import com.example.solimus.enums.ProviderRequestSort;
import com.example.solimus.dtos.provider.eligibility.ProviderEligibilityDTO;
import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.syndic.settings.EstimatedDelayDTO;
import com.example.solimus.dtos.provider.request.CreateQuoteDTO;
import com.example.solimus.dtos.provider.request.ProviderRequestDetailDTO;
import com.example.solimus.dtos.provider.request.ProviderRequestsDTO;
import com.example.solimus.dtos.provider.request.UpdateQuoteDTO;
import com.example.solimus.enums.ProviderRequestDisplayStatus;
import com.example.solimus.services.provider.request.ProviderRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@PreAuthorize("hasAuthority('ROLE_PRESTATAIRE')")
@Tag(name = "Prestataire - Demandes", description = "Demandes de travaux notifiées au prestataire")
public class ProviderRequestController {

    private final ProviderRequestService providerRequestService;

    @Operation(summary = "Lister mes demandes de travaux (notifiées, non assignées)",
            description = "Triées par distance (la plus proche d'abord, distanceKm sur chaque ligne) ou par date. "
                    + "Accès réservé aux prestataires dont le wallet dépasse le seuil (1000 FCFA) : sinon eligible=false, "
                    + "liste vide et eligibilityMessage. Les demandes sont diffusées au plus proche d'abord (5 km), "
                    + "puis le rayon s'élargit par paliers.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = ProviderRequestsDTO.class)))
    })
    @GetMapping
    public ResponseEntity<ProviderRequestsDTO> getAvailableRequests(

            // Filtre optionnel par statut affiché — si absent, retourne tout
            @RequestParam(required = false) ProviderRequestDisplayStatus status,

            // Recherche par titre de la demande ou nom de la résidence — si absent, pas de filtre
            @RequestParam(required = false) String search,

            // Tri : DISTANCE (défaut, la plus proche d'abord) ou RECENT (la plus récente d'abord)
            @RequestParam(defaultValue = "DISTANCE") ProviderRequestSort sortBy,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageRequest pageable = PageRequest.of(page, size);

        ProviderRequestsDTO result = providerRequestService
                .getAvailableRequests(status, search, sortBy, pageable);

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Vérifier mon accès aux demandes de travaux",
            description = "Solde du wallet, seuil (strictement supérieur à 1000 FCFA) et montant manquant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Accès renvoyé",
                    content = @Content(schema = @Schema(implementation = ProviderEligibilityDTO.class)))
    })
    @GetMapping("/eligibility")
    public ResponseEntity<ProviderEligibilityDTO> getEligibility() {
        return ResponseEntity.ok(providerRequestService.getEligibility());
    }

    @Operation(summary = "Estimer la commission Solimus d'un devis",
            description = "À appeler dans le formulaire de devis quand le total TTC change. La commission est débitée "
                    + "automatiquement du wallet à la fin des travaux ; un devis n'est accepté que si le wallet la couvre.")
    @GetMapping("/{id}/commission-estimate")
    public ResponseEntity<com.example.solimus.dtos.provider.eligibility.CommissionEstimateDTO> estimateCommission(
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.Parameter(description = "Montant TTC du devis (FCFA)", required = true)
            @RequestParam java.math.BigDecimal amount) {
        return ResponseEntity.ok(providerRequestService.estimateCommission(id, amount));
    }

    @Operation(summary = "Voir les détails d'une demande de travaux")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = ProviderRequestDetailDTO.class))),
            @ApiResponse(responseCode = "403", description = "Un autre prestataire a été sélectionné pour cette intervention, ou solde du wallet insuffisant (≤ 1000 FCFA)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Demande introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProviderRequestDetailDTO> getRequestDetails(@PathVariable Long id) {
        return ResponseEntity.ok(providerRequestService.getRequestDetails(id));
    }

    @Operation(summary = "Créer un devis (Brouillon ou Envoi)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Devis enregistré avec succès"),
            @ApiResponse(responseCode = "400", description = "Cette demande n'accepte plus de nouveaux devis, ou vous avez déjà soumis un devis pour cette demande",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Demande introuvable (ou vous n'y êtes pas rattaché), ou délai estimé introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/quote")
    public ResponseEntity<String> createQuote(@RequestBody @Valid CreateQuoteDTO dto) {
        providerRequestService.createQuote(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body("Devis enregistré avec succès.");
    }

    @Operation(summary = "Lister les délais d'estimation disponibles")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = EstimatedDelayDTO.class)))
    })
    @GetMapping("quote/estimated-delays")
    public ResponseEntity<List<EstimatedDelayDTO>> getEstimatedDelays(
            @RequestParam Long interventionRequestId) {
        return ResponseEntity.ok(providerRequestService.getEstimatedDelays(interventionRequestId));
    }

    @Operation(summary = "Mettre à jour partiellement un devis (uniquement si pas encore accepté)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Devis mis à jour avec succès"),
            @ApiResponse(responseCode = "400", description = "Impossible de modifier un devis qui a été accepté",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à modifier ce devis",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Devis introuvable, ou délai estimé introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PatchMapping("/quote/{id}")
    public ResponseEntity<Void> updateQuote(
            @PathVariable Long id,
            @RequestBody @Valid UpdateQuoteDTO dto) {
        providerRequestService.updateQuote(id, dto);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Supprimer un devis (uniquement si pas encore accepté)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Devis supprimé avec succès"),
            @ApiResponse(responseCode = "400", description = "Impossible de supprimer un devis qui a été accepté",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas autorisé à supprimer ce devis",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Devis introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @DeleteMapping("/quote/{id}")
    public ResponseEntity<Void> deleteQuote(@PathVariable Long id) {
        providerRequestService.deleteQuote(id);
        return ResponseEntity.noContent().build();
    }
}

package com.example.solimus.controllers;

import com.example.solimus.dtos.admin.subscription.*;
import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.syndic.subscription.SyndicPlanChangeResponseDTO;
import com.example.solimus.dtos.syndic.subscription.SyndicSubscriptionHistoryDTO;
import com.example.solimus.enums.ProviderPlanFeature;
import com.example.solimus.enums.SubscriberType;
import com.example.solimus.enums.SubscriptionStatus;
import com.example.solimus.enums.SyndicPlanFeature;
import com.example.solimus.services.admin.subscription.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/subscriptions")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Administration - Abonnements")
public class AdminSubscriptionController {

    private final PlanService planService;

    // ===== KPIs =====

    @Operation(summary = "KPIs de la page Gestion des abonnements")
    @GetMapping("/kpis")
    public ResponseEntity<SubscriptionKpiDTO> getSubscriptionKpis() {
        return ResponseEntity.ok(planService.getSubscriptionKpis());
    }

    // ===== Listing unifié (Syndic + Prestataire) =====

    @Operation(summary = "Liste unifiée de toutes les formules (Syndic + Prestataire)")
    @GetMapping("/plans")
    public ResponseEntity<List<PlanOverviewDTO>> getAllPlansOverview() {
        return ResponseEntity.ok(planService.getAllPlansOverview());
    }

    @Operation(summary = "Créer une nouvelle formule syndic")
    @PostMapping("/syndic-plans")
    public ResponseEntity<SyndicPlanDTO> createSyndicPlan(@RequestBody SyndicPlanRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.createSyndicPlan(dto));
    }

    @Operation(summary = "Modifier une formule syndic existante")
    @PatchMapping("/syndic-plans/{id}")
    public ResponseEntity<SyndicPlanDTO> updateSyndicPlan(
            @PathVariable Long id,
            @RequestBody SyndicPlanRequestDTO dto) {
        return ResponseEntity.ok(planService.updateSyndicPlan(id, dto));
    }

    @Operation(summary = "Supprimer une formule syndic")
    @DeleteMapping("/syndic-plans/{id}")
    public ResponseEntity<String> deleteSyndicPlan(@PathVariable Long id) {
        planService.deleteSyndicPlan(id);
        return ResponseEntity.ok("Formule supprimée avec succès");
    }

    @Operation(summary = "Activer ou désactiver une formule syndic")
    @PatchMapping("/syndic-plans/{id}/status")
    public ResponseEntity<SyndicPlanDTO> toggleSyndicPlanStatus(
            @PathVariable Long id,
            @RequestParam boolean active) {
        return ResponseEntity.ok(planService.toggleSyndicPlanStatus(id, active));
    }

    @Operation(summary = "Liste de toutes les fonctionnalités disponibles pour les formules syndic")
    @GetMapping("/syndic-plan-features")
    public ResponseEntity<List<SyndicPlanFeatureDTO>> getAllSyndicPlanFeatures() {

        List<SyndicPlanFeatureDTO> features = new ArrayList<>();
        for (SyndicPlanFeature feature : SyndicPlanFeature.values()) {
            features.add(SyndicPlanFeatureDTO.builder()
                    .value(feature.name())
                    .label(feature.getLabel())
                    .build());
        }

        return ResponseEntity.ok(features);
    }

    // ===== Formules Prestataire (CRUD) =====

    @Operation(summary = "Créer une nouvelle formule prestataire")
    @PostMapping("/provider-plans")
    public ResponseEntity<ProviderPlanDTO> createProviderPlan(@RequestBody ProviderPlanRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.createProviderPlan(dto));
    }

    @Operation(summary = "Modifier une formule prestataire existante")
    @PatchMapping("/provider-plans/{id}")
    public ResponseEntity<ProviderPlanDTO> updateProviderPlan(
            @PathVariable Long id,
            @RequestBody ProviderPlanRequestDTO dto) {
        return ResponseEntity.ok(planService.updateProviderPlan(id, dto));
    }

    @Operation(summary = "Activer ou désactiver une formule prestataire")
    @PatchMapping("/provider-plans/{id}/status")
    public ResponseEntity<ProviderPlanDTO> toggleProviderPlanStatus(
            @PathVariable Long id,
            @RequestParam boolean active) {
        return ResponseEntity.ok(planService.toggleProviderPlanStatus(id, active));
    }

    @Operation(summary = "Supprimer une formule prestataire")
    @DeleteMapping("/provider-plans/{id}")
    public ResponseEntity<String> deleteProviderPlan(@PathVariable Long id) {
        planService.deleteProviderPlan(id);
        return ResponseEntity.ok("Formule supprimée avec succès");
    }

    @Operation(summary = "Liste de toutes les fonctionnalités disponibles pour les formules prestataire")
    @GetMapping("/provider-plan-features")
    public ResponseEntity<List<SyndicPlanFeatureDTO>> getAllProviderPlanFeatures() {

        List<SyndicPlanFeatureDTO> features = new ArrayList<>();
        for (ProviderPlanFeature feature : ProviderPlanFeature.values()) {
            features.add(SyndicPlanFeatureDTO.builder()
                    .value(feature.name())
                    .label(feature.getLabel())
                    .build());
        }

        return ResponseEntity.ok(features);
    }

    // ===== Liste unifiée des abonnés (Syndic + Prestataire) =====

    @Operation(summary = "Liste paginée des abonnés (Syndic + Prestataire), avec recherche et filtres")
    @GetMapping("/subscribers")
    public ResponseEntity<SubscriberListResponseDTO> getAllSubscribers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) SubscriberType subscriberType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(planService.getAllSubscribers(search, status, subscriberType, page, size));
    }

    @Operation(summary = "Détail d'un abonné précis (page ouverte via l'icône œil de la liste)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail renvoyé avec succès"),
            @ApiResponse(responseCode = "400", description = "Requête invalide",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Aucun abonnement avec cet id",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/subscribers/{subscriptionId}")
    public ResponseEntity<SubscriberDetailDTO> getSubscriberDetail(
            @PathVariable Long subscriptionId,
            @RequestParam SubscriberType subscriberType) {
        return ResponseEntity.ok(planService.getSubscriberDetail(subscriptionId, subscriberType));
    }

    @Operation(summary = "Historique paginé des paiements d'un abonné précis (vu par l'admin)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page d'historique renvoyée avec succès"),
            @ApiResponse(responseCode = "400", description = "Requête invalide",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Aucun abonnement avec cet id",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/subscribers/{subscriptionId}/payment-history")
    public ResponseEntity<Page<SyndicSubscriptionHistoryDTO>> getSubscriberPaymentHistory(
            @PathVariable Long subscriptionId,
            @RequestParam SubscriberType subscriberType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(planService.getSubscriberPaymentHistory(subscriptionId, subscriberType, page, size));
    }

    @Operation(summary = "Bloc \"Détails du client\" léger — pour l'en-tête des modales Suspendre/Réactiver")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Infos renvoyées avec succès"),
            @ApiResponse(responseCode = "400", description = "Requête invalide",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Aucun abonnement avec cet id",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/subscribers/{subscriptionId}/quick-info")
    public ResponseEntity<SubscriberQuickInfoDTO> getSubscriberQuickInfo(
            @PathVariable Long subscriptionId,
            @RequestParam SubscriberType subscriberType) {
        return ResponseEntity.ok(planService.getSubscriberQuickInfo(subscriptionId, subscriberType));
    }

    @Operation(summary = "Suspendre le compte d'un abonné (bloque le login + désactive l'abonnement)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Compte suspendu avec succès"),
            @ApiResponse(responseCode = "400", description = "\"reason\" manquant/vide (validation)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Aucun abonnement avec cet id",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/subscribers/{subscriptionId}/suspend")
    public ResponseEntity<String> suspendSubscriber(
            @PathVariable Long subscriptionId,
            @RequestParam SubscriberType subscriberType,
            @Valid @RequestBody SuspendSubscriberDTO dto) {
        planService.suspendSubscriber(subscriptionId, subscriberType, dto);
        return ResponseEntity.ok("Compte suspendu avec succès");
    }

    @Operation(summary = "Réactiver le compte d'un abonné précédemment suspendu")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Compte réactivé avec succès"),
            @ApiResponse(responseCode = "400", description = "Ce compte n'est pas suspendu",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Aucun abonnement avec cet id",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/subscribers/{subscriptionId}/reactivate")
    public ResponseEntity<String> reactivateSubscriber(
            @PathVariable Long subscriptionId,
            @RequestParam SubscriberType subscriberType,
            @RequestParam(defaultValue = "true") boolean notifyClient) {
        planService.reactivateSubscriber(subscriptionId, subscriberType, notifyClient);
        return ResponseEntity.ok("Compte réactivé avec succès");
    }

    @Operation(summary = "Bloc \"Détails du client\" léger — pour l'en-tête de la modale Renouveler")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Infos renvoyées avec succès"),
            @ApiResponse(responseCode = "400", description = "Requête invalide",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Aucun abonnement avec cet id",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/subscribers/{subscriptionId}/renewal-info")
    public ResponseEntity<SubscriberRenewalInfoDTO> getSubscriberRenewalInfo(
            @PathVariable Long subscriptionId,
            @RequestParam SubscriberType subscriberType) {
        return ResponseEntity.ok(planService.getSubscriberRenewalInfo(subscriptionId, subscriberType));
    }

    @Operation(summary = "Options du formulaire de renouvellement (formules actives + durées disponibles, syndic ou prestataire)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Options renvoyées avec succès")
    })
    @GetMapping("/syndic-plans/renewal-options")
    public ResponseEntity<RenewalFormOptionsDTO> getRenewalFormOptions(@RequestParam SubscriberType subscriberType) {
        return ResponseEntity.ok(planService.getRenewalFormOptions(subscriberType));
    }

    @Operation(summary = "Renouveler manuellement l'abonnement d'un abonné, syndic ou prestataire (lien de paiement TouchPay à ouvrir)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Renouvellement initié — utiliser paymentUrl pour ouvrir TouchPay"),
            @ApiResponse(responseCode = "400", description = "Un paiement est déjà en attente, ou formule indisponible/sans tarif configuré",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Abonnement ou formule introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/subscribers/{subscriptionId}/renew")
    public ResponseEntity<SyndicPlanChangeResponseDTO> renewSubscriber(
            @PathVariable Long subscriptionId,
            @RequestParam SubscriberType subscriberType,
            @Valid @RequestBody AdminRenewSubscriptionDTO dto) {
        return ResponseEntity.ok(planService.renewSubscriber(subscriptionId, subscriberType, dto));
    }
}
package com.example.solimus.controllers;

import com.example.solimus.dtos.admin.subscription.*;
import com.example.solimus.enums.ProviderPlanFeature;
import com.example.solimus.enums.SubscriberType;
import com.example.solimus.enums.SubscriptionStatus;
import com.example.solimus.enums.SyndicPlanFeature;
import com.example.solimus.services.admin.subscription.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/subscriptions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
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
}
package com.example.solimus.controllers;

import com.example.solimus.dtos.admin.notification.AdminNotificationPreferenceDTO;
import com.example.solimus.dtos.admin.notification.UpdateAdminNotificationPreferenceDTO;
import com.example.solimus.dtos.admin.settings.PlatformSettingsDTO;
import com.example.solimus.dtos.admin.settings.UpdatePlatformSettingsDTO;
import com.example.solimus.dtos.admin.settings.UpdateWithdrawalSettingsDTO;
import com.example.solimus.dtos.admin.settings.WithdrawalSettingsDTO;
import com.example.solimus.services.admin.notification.AdminNotificationPreferenceService;
import com.example.solimus.services.admin.settings.PlatformSettingsService;
import com.example.solimus.services.admin.settings.WithdrawalSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Réglages globaux de l'admin
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Administration - Réglages")
public class AdminSettingsController {

    private final WithdrawalSettingsService withdrawalSettingsService;
    private final PlatformSettingsService platformSettingsService;
    private final AdminNotificationPreferenceService adminNotificationPreferenceService;

    @Operation(summary = "Limite mensuelle de retrait actuellement configurée")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Limite renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = WithdrawalSettingsDTO.class)))
    })
    @GetMapping("/withdrawal-settings")
    public ResponseEntity<WithdrawalSettingsDTO> getMonthlyLimit() {
        return ResponseEntity.ok(withdrawalSettingsService.getMonthlyLimit());
    }

    @Operation(summary = "Modifier la limite mensuelle de retrait")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Limite modifiée avec succès",
                    content = @Content(schema = @Schema(implementation = WithdrawalSettingsDTO.class)))
    })
    @PutMapping("/withdrawal-settings")
    public ResponseEntity<WithdrawalSettingsDTO> updateMonthlyLimit(
            @Valid @RequestBody UpdateWithdrawalSettingsDTO dto) {
        return ResponseEntity.ok(withdrawalSettingsService.updateMonthlyLimit(dto));
    }

    // ===== BLOC 2 — INFORMATIONS PLATEFORME =====

    @Operation(summary = "Réglages globaux de la plateforme actuellement configurés")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réglages renvoyés avec succès",
                    content = @Content(schema = @Schema(implementation = PlatformSettingsDTO.class)))
    })
    @GetMapping("/platform-settings")
    public ResponseEntity<PlatformSettingsDTO> getPlatformSettings() {
        return ResponseEntity.ok(platformSettingsService.getPlatformSettings());
    }

    @Operation(summary = "Modifier les réglages globaux de la plateforme")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réglages modifiés avec succès",
                    content = @Content(schema = @Schema(implementation = PlatformSettingsDTO.class)))
    })
    @PutMapping("/platform-settings")
    public ResponseEntity<PlatformSettingsDTO> updatePlatformSettings(
            @Valid @RequestBody UpdatePlatformSettingsDTO dto) {
        return ResponseEntity.ok(platformSettingsService.updatePlatformSettings(dto));
    }

    // ===== BLOC — PRÉFÉRENCES DE NOTIFICATIONS ADMIN =====

    @Operation(summary = "Matrice complète des préférences de notification de l'admin connecté")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Préférences renvoyées avec succès",
                    content = @Content(schema = @Schema(implementation = AdminNotificationPreferenceDTO.class)))
    })
    @GetMapping("/notification-preferences")
    public ResponseEntity<List<AdminNotificationPreferenceDTO>> getNotificationPreferences() {
        return ResponseEntity.ok(adminNotificationPreferenceService.getMyPreferences());
    }

    @Operation(summary = "Mettre à jour la matrice des préférences de notification de l'admin connecté")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Préférences mises à jour avec succès",
                    content = @Content(schema = @Schema(implementation = AdminNotificationPreferenceDTO.class)))
    })
    @PutMapping("/notification-preferences")
    public ResponseEntity<List<AdminNotificationPreferenceDTO>> updateNotificationPreferences(
            @Valid @RequestBody List<UpdateAdminNotificationPreferenceDTO> updates) {
        return ResponseEntity.ok(adminNotificationPreferenceService.updateMyPreferences(updates));
    }
}
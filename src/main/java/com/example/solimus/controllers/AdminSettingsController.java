package com.example.solimus.controllers;

import com.example.solimus.dtos.admin.settings.PlatformSettingsDTO;
import com.example.solimus.dtos.admin.settings.UpdatePlatformSettingsDTO;
import com.example.solimus.dtos.admin.settings.UpdateWithdrawalSettingsDTO;
import com.example.solimus.dtos.admin.settings.WithdrawalSettingsDTO;
import com.example.solimus.services.admin.settings.PlatformSettingsService;
import com.example.solimus.services.admin.settings.WithdrawalSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// Réglages globaux de l'admin 
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Administration - Réglages")
public class AdminSettingsController {

    private final WithdrawalSettingsService withdrawalSettingsService;
    private final PlatformSettingsService platformSettingsService;

    @Operation(summary = "Limite mensuelle de retrait actuellement configurée")
    @GetMapping("/withdrawal-settings")
    public ResponseEntity<WithdrawalSettingsDTO> getMonthlyLimit() {
        return ResponseEntity.ok(withdrawalSettingsService.getMonthlyLimit());
    }

    @Operation(summary = "Modifier la limite mensuelle de retrait")
    @PutMapping("/withdrawal-settings")
    public ResponseEntity<WithdrawalSettingsDTO> updateMonthlyLimit(
            @Valid @RequestBody UpdateWithdrawalSettingsDTO dto) {
        return ResponseEntity.ok(withdrawalSettingsService.updateMonthlyLimit(dto));
    }

    // ===== BLOC 2 — INFORMATIONS PLATEFORME =====

    @Operation(summary = "Réglages globaux de la plateforme actuellement configurés")
    @GetMapping("/platform-settings")
    public ResponseEntity<PlatformSettingsDTO> getPlatformSettings() {
        return ResponseEntity.ok(platformSettingsService.getPlatformSettings());
    }

    @Operation(summary = "Modifier les réglages globaux de la plateforme")
    @PutMapping("/platform-settings")
    public ResponseEntity<PlatformSettingsDTO> updatePlatformSettings(
            @Valid @RequestBody UpdatePlatformSettingsDTO dto) {
        return ResponseEntity.ok(platformSettingsService.updatePlatformSettings(dto));
    }
}
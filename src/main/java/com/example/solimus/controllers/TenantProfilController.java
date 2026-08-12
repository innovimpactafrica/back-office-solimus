package com.example.solimus.controllers;

import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.owner.dashboard.NotificationListResponseDTO;
import com.example.solimus.dtos.syndic.settings.ChangePasswordDTO;
import com.example.solimus.dtos.tenant.profil.TenantProfileDTO;
import com.example.solimus.services.tenant.profil.TenantProfilService;
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

@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_LOCATAIRE')")
@Tag(name = "Locataire - Profil", description = "Consultation du profil du locataire")
public class TenantProfilController {

    private final TenantProfilService tenantProfilService;

    @Operation(summary = "Voir mon profil", tags = {"Locataire - Profil"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = TenantProfileDTO.class))),
            @ApiResponse(responseCode = "403", description = "Aucun bien assigné à ce compte locataire",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/profile")
    public ResponseEntity<TenantProfileDTO> getProfile() {
        return ResponseEntity.ok(tenantProfilService.getProfile());
    }

    @Operation(summary = "Changer mon mot de passe", tags = {"Locataire - Profil"})
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Mot de passe changé avec succès"),
            @ApiResponse(responseCode = "400", description = "Mot de passe actuel incorrect, ou confirmation ne correspondant pas au nouveau mot de passe",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PutMapping("/profile/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        tenantProfilService.changePassword(dto);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // NOTIFICATIONS (CLOCHE)
    // =========================================================================

    @Operation(summary = "Lister mes notifications", description = "Retourne la liste paginée des notifications du locataire connecté, pour la cloche", tags = {"Locataire - Profil"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = NotificationListResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/notifications")
    public ResponseEntity<NotificationListResponseDTO> getMyNotifications(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ResponseEntity.ok(tenantProfilService.getMyNotifications(page, size));
    }

    @Operation(summary = "Marquer toutes mes notifications comme lues", tags = {"Locataire - Profil"})
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Notifications marquées comme lues avec succès"),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PatchMapping("/notifications/mark-all-read")
    public ResponseEntity<Void> markAllNotificationsAsRead() {
        tenantProfilService.markAllNotificationsAsRead();
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // PRÉFÉRENCES DE NOTIFICATIONS
    // =========================================================================

    @Operation(summary = "Activer mes notifications", tags = {"Locataire - Profil"})
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Notifications activées avec succès"),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PatchMapping("/notifications/activate")
    public ResponseEntity<Void> activateNotifications() {
        tenantProfilService.activateNotifications();
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Désactiver mes notifications", tags = {"Locataire - Profil"})
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Notifications désactivées avec succès"),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PatchMapping("/notifications/deactivate")
    public ResponseEntity<Void> deactivateNotifications() {
        tenantProfilService.deactivateNotifications();
        return ResponseEntity.noContent().build();
    }
}

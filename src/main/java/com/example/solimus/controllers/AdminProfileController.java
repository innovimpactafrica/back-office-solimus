package com.example.solimus.controllers;

import com.example.solimus.dtos.admin.profile.AdminChangePasswordDTO;
import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.admin.profile.AdminProfileDTO;
import com.example.solimus.dtos.admin.profile.ChangePasswordResultDTO;
import com.example.solimus.dtos.admin.profile.UpdateAdminProfileDTO;
import com.example.solimus.dtos.owner.dashboard.NotificationListResponseDTO;
import com.example.solimus.services.admin.profile.AdminProfileService;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/profile")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Administration - Mon Profil")
public class AdminProfileController {

    private final AdminProfileService adminProfileService;

    // ===== BLOC 1 — INFORMATIONS PERSONNELLES =====

    @Operation(summary = "Informations personnelles de l'admin connecté")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = AdminProfileDTO.class)))
    })
    @GetMapping
    public ResponseEntity<AdminProfileDTO> getMyProfile() {
        return ResponseEntity.ok(adminProfileService.getMyProfile());
    }

    @Operation(summary = "Mettre à jour les informations personnelles de l'admin connecté")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil mis à jour avec succès",
                    content = @Content(schema = @Schema(implementation = AdminProfileDTO.class))),
            @ApiResponse(responseCode = "409", description = "Cet email ou ce numéro de téléphone est déjà utilisé par un autre compte",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PutMapping
    public ResponseEntity<AdminProfileDTO> updateMyProfile(@Valid @RequestBody UpdateAdminProfileDTO dto) {
        return ResponseEntity.ok(adminProfileService.updateMyProfile(dto));
    }

    @Operation(summary = "Ajouter ou remplacer la photo de profil de l'admin connecté")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Photo mise à jour avec succès",
                    content = @Content(schema = @Schema(implementation = AdminProfileDTO.class))),
            @ApiResponse(responseCode = "400", description = "Aucune photo fournie, format non supporté (JPG ou PNG uniquement), ou fichier trop volumineux (max 2 Mo)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping(value = "/photo", consumes = "multipart/form-data")
    public ResponseEntity<AdminProfileDTO> updateProfilePhoto(@RequestPart("photo") MultipartFile photo) {
        return ResponseEntity.ok(adminProfileService.updateProfilePhoto(photo));
    }

    // ===== BLOC 3 — CHANGEMENT DE MOT DE PASSE =====

    @Operation(summary = "Changer le mot de passe de l'admin connecté")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mot de passe changé avec succès",
                    content = @Content(schema = @Schema(implementation = ChangePasswordResultDTO.class))),
            @ApiResponse(responseCode = "400", description = "Les mots de passe ne correspondent pas, mot de passe actuel incorrect, "
                    + "ou le nouveau mot de passe est trop court",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/change-password")
    public ResponseEntity<ChangePasswordResultDTO> changePassword(@Valid @RequestBody AdminChangePasswordDTO dto) {
        return ResponseEntity.ok(adminProfileService.changePassword(dto));
    }

    // ===== NOTIFICATIONS (cloche) =====

    @Operation(summary = "Lister mes notifications (paginé)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = NotificationListResponseDTO.class)))
    })
    @GetMapping("/notifications")
    public ResponseEntity<NotificationListResponseDTO> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminProfileService.getMyNotifications(page, size));
    }

    @Operation(summary = "Marquer toutes mes notifications comme lues")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Notifications marquées comme lues avec succès")
    })
    @PatchMapping("/notifications/mark-all-read")
    public ResponseEntity<Void> markAllNotificationsAsRead() {
        adminProfileService.markAllNotificationsAsRead();
        return ResponseEntity.noContent().build();
    }
}
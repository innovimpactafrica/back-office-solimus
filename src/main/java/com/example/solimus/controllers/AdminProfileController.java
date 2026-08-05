package com.example.solimus.controllers;

import com.example.solimus.dtos.admin.profile.AdminChangePasswordDTO;
import com.example.solimus.dtos.admin.profile.AdminProfileDTO;
import com.example.solimus.dtos.admin.profile.ChangePasswordResultDTO;
import com.example.solimus.dtos.admin.profile.UpdateAdminProfileDTO;
import com.example.solimus.services.admin.profile.AdminProfileService;
import io.swagger.v3.oas.annotations.Operation;
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
    @GetMapping
    public ResponseEntity<AdminProfileDTO> getMyProfile() {
        return ResponseEntity.ok(adminProfileService.getMyProfile());
    }

    @Operation(summary = "Mettre à jour les informations personnelles de l'admin connecté")
    @PutMapping
    public ResponseEntity<AdminProfileDTO> updateMyProfile(@Valid @RequestBody UpdateAdminProfileDTO dto) {
        return ResponseEntity.ok(adminProfileService.updateMyProfile(dto));
    }

    @Operation(summary = "Ajouter ou remplacer la photo de profil de l'admin connecté")
    @PostMapping(value = "/photo", consumes = "multipart/form-data")
    public ResponseEntity<AdminProfileDTO> updateProfilePhoto(@RequestPart("photo") MultipartFile photo) {
        return ResponseEntity.ok(adminProfileService.updateProfilePhoto(photo));
    }

    // ===== BLOC 3 — CHANGEMENT DE MOT DE PASSE =====

    @Operation(summary = "Changer le mot de passe de l'admin connecté")
    @PostMapping("/change-password")
    public ResponseEntity<ChangePasswordResultDTO> changePassword(@Valid @RequestBody AdminChangePasswordDTO dto) {
        return ResponseEntity.ok(adminProfileService.changePassword(dto));
    }
}
package com.example.solimus.controllers;

import com.example.solimus.dtos.profile.CoOwnerProfileDTO;
import com.example.solimus.dtos.profile.UpdateCoOwnerProfileDTO;
import com.example.solimus.services.profile.CoOwnerProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coproprietaire/profile")
@RequiredArgsConstructor
@Tag(name = "CoOwner - Profile", description = "Gestion du profil du copropriétaire")
public class CoOwnerProfileController {

    private final CoOwnerProfileService profileService;

    @Operation(summary = "Récupérer mon profil")
    @GetMapping
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<CoOwnerProfileDTO> getProfile() {
        return ResponseEntity.ok(profileService.getProfile());
    }

    @Operation(summary = "Mettre à jour mon profil")
    @PutMapping
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<CoOwnerProfileDTO> updateProfile(
            @Valid @RequestBody UpdateCoOwnerProfileDTO dto) {
        return ResponseEntity.ok(profileService.updateProfile(dto));
    }
}

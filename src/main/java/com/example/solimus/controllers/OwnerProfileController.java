package com.example.solimus.controllers;

import com.example.solimus.dtos.profile.CoOwnerProfileDTO;
import com.example.solimus.dtos.profile.UpdateCoOwnerProfileDTO;

import com.example.solimus.services.profile.CoOwnerProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/coowner/profile")
@RequiredArgsConstructor
@Tag(name = "Copropriétaire - Profil", description = "Consultation et mise à jour du profil du copropriétaire.")
public class OwnerProfileController {

    private final CoOwnerProfileService profileService;

    @Operation(summary = "Voir mon profil")
    @GetMapping
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<CoOwnerProfileDTO> getProfile() {
        return ResponseEntity.ok(profileService.getProfile());
    }

    @Operation(summary = "Modifier mon profil", description = "Permet de modifier : prénom, nom, téléphone et photo de profil. L'email et le bien sont non modifiables.")
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<CoOwnerProfileDTO> updateProfile(
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) MultipartFile photo) {
        UpdateCoOwnerProfileDTO dto = UpdateCoOwnerProfileDTO.builder()
                .firstName(firstName)
                .lastName(lastName)
                .phone(phone)
                .build();

        return ResponseEntity.ok(profileService.updateProfile(dto, photo));
    }
}

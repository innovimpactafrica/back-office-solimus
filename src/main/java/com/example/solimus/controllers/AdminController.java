package com.example.solimus.controllers;



import com.example.solimus.dtos.admin.CreateSpecialtyDTO;
import com.example.solimus.dtos.admin.CreateUserRequestDTO;
import com.example.solimus.dtos.admin.CreateUserResponseDTO;

import com.example.solimus.dtos.admin.EstimatedDelayDTO;
import com.example.solimus.dtos.admin.SpecialtyDTO;
import com.example.solimus.dtos.admin.UserListResponseDTO;
import com.example.solimus.dtos.residence.CreateSecurityFeatureDTO;
import com.example.solimus.dtos.residence.SecurityFeatureDTO;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.UserStatus;
import com.example.solimus.services.admin.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ============================================================================
    // 🛠 GESTION DES SPÉCIALITÉS
    // ============================================================================

    @Operation(summary = "Liste de toutes les spécialités", tags = {"2.b Administration - Spécialités"})
    @GetMapping("/specialties")
    public ResponseEntity<List<SpecialtyDTO>> getAllSpecialties() {
        return ResponseEntity.ok(adminService.getAllSpecialties());
    }

    @Operation(summary = "Créer une nouvelle spécialité", tags = {"2.b Administration - Spécialités"})
    @PostMapping("/specialties")
    public ResponseEntity<SpecialtyDTO> createSpecialty(@RequestBody @Valid CreateSpecialtyDTO specialtyDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createSpecialty(specialtyDTO));
    }

    @Operation(summary = "Modifier une spécialité", tags = {"2.b Administration - Spécialités"})
    @PutMapping("/specialties/{id}")
    public ResponseEntity<SpecialtyDTO> updateSpecialty(@PathVariable Long id, @RequestBody @Valid CreateSpecialtyDTO specialtyDTO) {
        return ResponseEntity.ok(adminService.updateSpecialty(id, specialtyDTO));
    }

    @Operation(summary = "Supprimer une spécialité", tags = {"2.b Administration - Spécialités"})
    @DeleteMapping("/specialties/{id}")
    public ResponseEntity<Void> deleteSpecialty(@PathVariable Long id) {
        adminService.deleteSpecialty(id);
        return ResponseEntity.noContent().build();
    }



    // ============================================================================
    // ⏳ GESTION DES DÉLAIS
    // ============================================================================

    @Operation(summary = "Liste de tous les délais estimés", tags = {"2.c Administration - Délais"})
    @GetMapping("/delays")
    public ResponseEntity<List<EstimatedDelayDTO>> getAllEstimatedDelays() {
        return ResponseEntity.ok(adminService.getAllEstimatedDelays());
    }

    @Operation(summary = "Ajouter un nouveau délai estimé", tags = {"2.c Administration - Délais"})
    @PostMapping("/delays")
    public ResponseEntity<EstimatedDelayDTO> addEstimatedDelay(
            @RequestParam String label,
            @RequestParam Integer days) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.addEstimatedDelay(label, days));
    }

    // ============================================================================
    // 👤 GESTION DES UTILISATEURS
    // ============================================================================

    @Operation(
            summary = "Créer un compte utilisateur",
            description = "L'admin saisit les informations de l'utilisateur. Un email d'activation est envoyé automatiquement.",
            tags = {"2.a Administration - Utilisateurs"}
    )
    @PostMapping("/users")
    public ResponseEntity<CreateUserResponseDTO> createUser(
            @RequestBody @Valid CreateUserRequestDTO dto) {
        CreateUserResponseDTO response = adminService.createUser(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Liste paginée des utilisateurs internes avec filtres (recherche, rôle, statut). Exclut les prestataires et copropriétaires.", tags = {"2.a Administration - Utilisateurs"})
    @GetMapping("/users")
    public ResponseEntity<UserListResponseDTO> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ERole role,
            @RequestParam(required = false) UserStatus status) {
        return ResponseEntity.ok(adminService.getUsers(page, size, search, role, status));
    }

    @Operation(summary = "Modifier le statut d'un utilisateur (Activer/Désactiver)", tags = {"2.a Administration - Utilisateurs"})
    @PatchMapping("/users/{id}/status")
    public ResponseEntity<Void> updateUserStatus(
            @PathVariable Long id,
            @RequestParam UserStatus status) {
        adminService.updateUserStatus(id, status);
        return ResponseEntity.ok().build();
    }

    // ============================================================================
    // GESTION DES OPTIONS DE SÉCURITÉ
    // ============================================================================

    @Operation(summary = "Créer une option de sécurité", tags = {"2.e Administration - Options de sécurité"})
    @PostMapping("/security-features")
    public ResponseEntity<Void> createSecurityFeature(@RequestBody @Valid CreateSecurityFeatureDTO dto) {
        adminService.createSecurityFeature(dto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "Lister toutes les options de sécurité (actives ou non)", tags = {"2.e Administration - Options de sécurité"})
    @GetMapping("/security-features")
    public ResponseEntity<List<SecurityFeatureDTO>> getSecurityFeatures() {
        return ResponseEntity.ok(adminService.getSecurityFeatures());
    }

    @Operation(summary = "Désactiver une option de sécurité", tags = {"2.e Administration - Options de sécurité"})
    @PatchMapping("/security-features/{id}/deactivate")
    public ResponseEntity<Void> deactivateSecurityFeature(@PathVariable Long id) {
        adminService.deactivateSecurityFeature(id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Activer une option de sécurité", tags = {"2.e Administration - Options de sécurité"})
    @PatchMapping("/security-features/{id}/activate")
    public ResponseEntity<Void> activateSecurityFeature(@PathVariable Long id) {
        adminService.activateSecurityFeature(id);
        return ResponseEntity.ok().build();
    }
    
}

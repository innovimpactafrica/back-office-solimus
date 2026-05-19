package com.example.solimus.controllers;



import com.example.solimus.dtos.admin.CreateSpecialtyDTO;
import com.example.solimus.dtos.admin.CreateUserRequestDTO;
import com.example.solimus.dtos.admin.CreateUserResponseDTO;

import com.example.solimus.dtos.admin.EstimatedDelayDTO;
import com.example.solimus.dtos.admin.SpecialtyDTO;
import com.example.solimus.dtos.admin.UserListResponseDTO;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.UserStatus;
import com.example.solimus.services.admin.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Administration", description = "API pour la gestion des paramètres de l'application (Admin uniquement)")
public class AdminController {

    private final AdminService adminService;

    // ============================================================================
    // 🛠 GESTION DES SPÉCIALITÉS
    // ============================================================================

    @Operation(summary = "Liste de toutes les spécialités")
    @GetMapping("/specialties")
    public ResponseEntity<List<SpecialtyDTO>> getAllSpecialties() {
        return ResponseEntity.ok(adminService.getAllSpecialties());
    }

    @Operation(summary = "Créer une nouvelle spécialité")
    @PostMapping("/specialties")
    public ResponseEntity<SpecialtyDTO> createSpecialty(@RequestBody @Valid CreateSpecialtyDTO specialtyDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createSpecialty(specialtyDTO));
    }

    @Operation(summary = "Modifier une spécialité")
    @PutMapping("/specialties/{id}")
    public ResponseEntity<SpecialtyDTO> updateSpecialty(@PathVariable Long id, @RequestBody @Valid CreateSpecialtyDTO specialtyDTO) {
        return ResponseEntity.ok(adminService.updateSpecialty(id, specialtyDTO));
    }

    @Operation(summary = "Supprimer une spécialité")
    @DeleteMapping("/specialties/{id}")
    public ResponseEntity<Void> deleteSpecialty(@PathVariable Long id) {
        adminService.deleteSpecialty(id);
        return ResponseEntity.noContent().build();
    }



    // ============================================================================
    // ⏳ GESTION DES DÉLAIS
    // ============================================================================

    @Operation(summary = "Liste de tous les délais estimés")
    @GetMapping("/delays")
    public ResponseEntity<List<EstimatedDelayDTO>> getAllEstimatedDelays() {
        return ResponseEntity.ok(adminService.getAllEstimatedDelays());
    }

    @Operation(summary = "Ajouter un nouveau délai estimé")
    @PostMapping("/delays")
    public ResponseEntity<EstimatedDelayDTO> addEstimatedDelay(
            @RequestParam String label,
            @RequestParam(required = false) Integer days) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.addEstimatedDelay(label, days));
    }

    // ============================================================================
    // 👤 GESTION DES UTILISATEURS
    // ============================================================================

    @Operation(summary = "Liste paginée des utilisateurs avec filtres (recherche, rôle, statut)")
    @GetMapping("/users")
    public ResponseEntity<UserListResponseDTO> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ERole role,
            @RequestParam(required = false) UserStatus status) {
        return ResponseEntity.ok(adminService.getUsers(page, size, search, role, status));
    }

    @Operation(summary = "Modifier le statut d'un utilisateur (Activer/Désactiver)")
    @PatchMapping("/users/{id}/status")
    public ResponseEntity<Void> updateUserStatus(
            @PathVariable Long id,
            @RequestParam UserStatus status) {
        adminService.updateUserStatus(id, status);
        return ResponseEntity.ok().build();
    }

    // ============================================================================
    // 💼 CRÉATION DE COMPTE UTILISATEUR
    // ============================================================================

    @Operation(
        summary = "Créer un compte utilisateur",
        description = "L'admin saisit les informations de l'utilisateur. Un email d'activation est envoyé automatiquement."
    )
    @PostMapping("/users")
    public ResponseEntity<CreateUserResponseDTO> createUser(
            @RequestBody @Valid CreateUserRequestDTO dto) {
        CreateUserResponseDTO response = adminService.createUser(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

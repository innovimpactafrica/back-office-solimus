package com.example.solimus.controllers;



import com.example.solimus.dtos.admin.CreateUserRequestDTO;
import com.example.solimus.dtos.admin.CreateUserResponseDTO;
import com.example.solimus.dtos.admin.EstimatedDelayDTO;
import com.example.solimus.dtos.admin.UserListResponseDTO;
import com.example.solimus.dtos.admin.settingsAdmin.ProviderPlanDTO;
import com.example.solimus.dtos.admin.settingsAdmin.ProviderPlanRequestDTO;
import com.example.solimus.dtos.provider.wallet.WithdrawalRequestDTO;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.UserStatus;
import com.example.solimus.services.admin.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
@Tag(name = "Administration")
public class AdminController {

    private final AdminService adminService;

    // ============================================================================
    //  GESTION DES RETRAITS WALLET
    // ============================================================================

    @Operation(summary = "Valider une demande de retrait wallet", tags = {"Administration - Retraits Wallet"})
    @PostMapping(value = "/wallet-withdrawals/{withdrawalId}/approve", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WithdrawalRequestDTO> approveWalletWithdrawal(
            @PathVariable Long withdrawalId,
            @RequestPart MultipartFile receipt,
            @RequestPart(required = false) String comment) {
        return ResponseEntity.ok(adminService.approveWalletWithdrawal(withdrawalId, receipt, comment));
    }

    @Operation(summary = "Refuser une demande de retrait wallet", tags = {"Administration - Retraits Wallet"})
    @PostMapping("/wallet-withdrawals/{withdrawalId}/reject")
    public ResponseEntity<WithdrawalRequestDTO> rejectWalletWithdrawal(
            @PathVariable Long withdrawalId,
            @RequestParam String rejectionReason) {
        return ResponseEntity.ok(adminService.rejectWalletWithdrawal(withdrawalId, rejectionReason));
    }

    // ============================================================================
    //  GESTION DES Abonnements
    // ============================================================================

    @Operation(summary = "Enregistrer le plan d'abonnement de prestataire", tags = {"Administration - Abonnements"})
    @PostMapping("/provider-plan")
    public ResponseEntity<ProviderPlanDTO> saveProviderPlan(
            @RequestBody @Valid ProviderPlanRequestDTO dto) {
        return ResponseEntity.ok(adminService.saveProviderPlan(dto));
    }

    // ============================================================================
    // ⏳ GESTION DES DÉLAIS
    // ============================================================================

    @Operation(summary = "Liste de tous les délais estimés", tags = {"Administration - Délais"})
    @GetMapping("/delays")
    public ResponseEntity<List<EstimatedDelayDTO>> getAllEstimatedDelays() {
        return ResponseEntity.ok(adminService.getAllEstimatedDelays());
    }

    @Operation(summary = "Ajouter un nouveau délai estimé", tags = {"Administration - Délais"})
    @PostMapping("/delays")
    public ResponseEntity<EstimatedDelayDTO> addEstimatedDelay(
            @RequestParam String label,
            @RequestParam Integer days) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.addEstimatedDelay(label, days));
    }

    @Operation(summary = "Supprimer un délai estimé", tags = {"Administration - Délais"})
    @DeleteMapping("/delays/{id}")
    public ResponseEntity<Void> deleteEstimatedDelay(@PathVariable Long id) {
        adminService.deleteEstimatedDelay(id);
        return ResponseEntity.noContent().build();
    }

    // ============================================================================
    // 👤 GESTION DES UTILISATEURS
    // ============================================================================

    @Operation(
            summary = "Créer un compte utilisateur",
            description = "L'admin saisit les informations de l'utilisateur. Un email d'activation est envoyé automatiquement.",
            tags = {"Administration - Utilisateurs"}
    )
    @PostMapping("/users")
    public ResponseEntity<CreateUserResponseDTO> createUser(
            @RequestBody @Valid CreateUserRequestDTO dto) {
        CreateUserResponseDTO response = adminService.createUser(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Liste paginée des utilisateurs internes avec filtres (recherche, rôle, statut). Exclut les prestataires et copropriétaires.", tags = {"Administration - Utilisateurs"})
    @GetMapping("/users")
    public ResponseEntity<UserListResponseDTO> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ERole role,
            @RequestParam(required = false) UserStatus status) {
        return ResponseEntity.ok(adminService.getUsers(page, size, search, role, status));
    }

    @Operation(summary = "Modifier le statut d'un utilisateur (Activer/Désactiver)", tags = {"Administration - Utilisateurs"})
    @PatchMapping("/users/{id}/status")
    public ResponseEntity<Void> updateUserStatus(
            @PathVariable Long id,
            @RequestParam UserStatus status) {
        adminService.updateUserStatus(id, status);
        return ResponseEntity.ok().build();
    }

}

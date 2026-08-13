package com.example.solimus.controllers;

import com.example.solimus.dtos.admin.withdrawal.*;
import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.enums.SubscriberType;
import com.example.solimus.enums.WithdrawalStatus;
import com.example.solimus.services.admin.withdrawalRequest.WithdrawalRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/withdrawal-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Administration - Demandes de retraits")
public class AdminWithdrawalRequestController {

    private final WithdrawalRequestService withdrawalRequestService;

    // ===== Bloc 1 =====

    @Operation(summary = "KPIs de la page \"Demandes de retraits\" (Syndic + Prestataire confondus)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "KPIs renvoyés avec succès",
                    content = @Content(schema = @Schema(implementation = WithdrawalDashboardKpiDTO.class)))
    })
    @GetMapping("/dashboard/kpis")
    public ResponseEntity<WithdrawalDashboardKpiDTO> getDashboardKpis() {
        return ResponseEntity.ok(withdrawalRequestService.getDashboardKpis());
    }

    // ===== Bloc 2 =====

    @Operation(summary = "Liste paginée des demandes de retrait (Syndic + Prestataire), avec recherche et filtre par statut")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = WithdrawalRequestRowDTO.class)))
    })
    @GetMapping
    public ResponseEntity<Page<WithdrawalRequestRowDTO>> getAllWithdrawalRequests(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) WithdrawalStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(withdrawalRequestService.getAllWithdrawalRequests(search, status, page, size));
    }

    // ===== Bloc 3 =====

    @Operation(summary = "Fiche détail d'une demande de retrait (en-tête, informations générales, analyse financière, derniers retraits, suivi)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail renvoyé avec succès"),
            @ApiResponse(responseCode = "404", description = "Aucune demande avec cet id",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<WithdrawalRequestDetailDTO> getWithdrawalRequestDetail(
            @PathVariable Long id,
            @RequestParam SubscriberType type) {
        return ResponseEntity.ok(withdrawalRequestService.getWithdrawalRequestDetail(id, type));
    }

    // ===== Bloc 4 =====

    @Operation(summary = "Récapitulatif affiché dans la modale de validation, avant confirmation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Récapitulatif renvoyé avec succès"),
            @ApiResponse(responseCode = "404", description = "Aucune demande avec cet id",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/{id}/summary")
    public ResponseEntity<WithdrawalValidationSummaryDTO> getValidationSummary(
            @PathVariable Long id,
            @RequestParam SubscriberType type) {
        return ResponseEntity.ok(withdrawalRequestService.getValidationSummary(id, type));
    }

    @Operation(summary = "Valider une demande de retrait (upload du reçu obligatoire, PDF/JPG/PNG, max 5 Mo)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Demande validée avec succès"),
            @ApiResponse(responseCode = "400", description = "Reçu manquant, format non supporté, ou fichier trop volumineux",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Aucune demande avec cet id",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Cette demande a déjà été traitée, OU le solde disponible actuel "
                    + "(retraits COMPLETED déduits, jamais les PENDING) ne couvre plus le montant demandé — peut survenir si "
                    + "une autre demande concurrente a déjà été validée entre-temps",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping(value = "/{id}/validate", consumes = "multipart/form-data")
    public ResponseEntity<WithdrawalActionResultDTO> validateWithdrawalRequest(
            @PathVariable Long id,
            @RequestParam SubscriberType type,
            @RequestParam("receipt") MultipartFile receipt,
            @RequestParam(required = false) String comment) {
        return ResponseEntity.ok(withdrawalRequestService.validateWithdrawalRequest(id, type, receipt, comment));
    }

    // ===== Bloc 5 =====

    @Operation(summary = "Refuser une demande de retrait")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Demande refusée avec succès"),
            @ApiResponse(responseCode = "400", description = "\"rejectionReason\" ou \"notifyUser\" manquant (validation)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Aucune demande avec cet id",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Cette demande a déjà été traitée",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/{id}/reject")
    public ResponseEntity<WithdrawalActionResultDTO> rejectWithdrawalRequest(
            @PathVariable Long id,
            @RequestParam SubscriberType type,
            @Valid @RequestBody RejectWithdrawalRequestDTO dto) {
        return ResponseEntity.ok(withdrawalRequestService.rejectWithdrawalRequest(id, type, dto));
    }
}
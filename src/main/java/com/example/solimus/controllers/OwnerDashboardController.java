package com.example.solimus.controllers;

import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.owner.dashboard.NotificationListResponseDTO;
import com.example.solimus.dtos.owner.dashboard.OwnerDashboardHeaderDTO;
import com.example.solimus.dtos.owner.dashboard.OwnerDashboardKpiDTO;
import com.example.solimus.dtos.owner.dashboard.OwnerPendingChargeDTO;
import com.example.solimus.dtos.owner.dashboard.OwnerPropertySelectorDTO;
import com.example.solimus.dtos.owner.meeting.OwnerMeetingCardDTO;
import com.example.solimus.services.owner.dashboard.CoOwnerDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coowner")
@RequiredArgsConstructor
@Tag(name = "Copropriétaire - Dashboard", description = "Dashboard du copropriétaire")
public class OwnerDashboardController {

    private final CoOwnerDashboardService dashboardService;

    @Operation(summary = "Liste des biens du copropriétaire connecté (sélecteur 'Mon bien')")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = OwnerPropertySelectorDTO.class)))
    })
    @GetMapping("/dashboard/properties")
    public ResponseEntity<List<OwnerPropertySelectorDTO>> getMyProperties() {
        return ResponseEntity.ok(dashboardService.getMyProperties());
    }

    @Operation(summary = "En-tête du dashboard (prénom, photo, compteur de notifications non lues)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "En-tête renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = OwnerDashboardHeaderDTO.class)))
    })
    @GetMapping("/dashboard/header")
    public ResponseEntity<OwnerDashboardHeaderDTO> getDashboardHeader() {
        return ResponseEntity.ok(dashboardService.getDashboardHeader());
    }

    @Operation(summary = "Liste paginée des notifications du copropriétaire connecté")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = NotificationListResponseDTO.class)))
    })
    @GetMapping("/dashboard/notifications")
    public ResponseEntity<NotificationListResponseDTO> getMyNotifications(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ResponseEntity.ok(dashboardService.getMyNotifications(page, size));
    }

    @Operation(summary = "Marque toutes les notifications du copropriétaire connecté comme lues")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifications marquées comme lues avec succès")
    })
    @PatchMapping("/dashboard/notifications/mark-all-read")
    public ResponseEntity<String> markAllNotificationsAsRead() {
        dashboardService.markAllNotificationsAsRead();
        return ResponseEntity.ok("Notifications marquées comme lues");
    }

    @Operation(summary = "KPIs du dashboard (Charge annuelle + Restant à payer) pour une résidence précise")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "KPIs renvoyés avec succès",
                    content = @Content(schema = @Schema(implementation = OwnerDashboardKpiDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'avez pas de lot dans cette résidence",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/dashboard/kpis")
    public ResponseEntity<OwnerDashboardKpiDTO> getDashboardKpis(
            @RequestParam Long residenceId) {
        return ResponseEntity.ok(dashboardService.getDashboardKpis(residenceId));
    }

    @Operation(summary = "Charges en attente pour le dashboard (aperçu limité)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = OwnerPendingChargeDTO.class)))
    })
    @GetMapping("/dashboard/pending-charges")
    public ResponseEntity<List<OwnerPendingChargeDTO>> getPendingCharges(
            @RequestParam Long residenceId) {
        return ResponseEntity.ok(dashboardService.getPendingCharges(residenceId));
    }

    @Operation(summary = "Prochaines réunions pour le dashboard (aperçu limité, résidence précise)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = OwnerMeetingCardDTO.class)))
    })
    @GetMapping("/dashboard/upcoming-meetings")
    public ResponseEntity<List<OwnerMeetingCardDTO>> getUpcomingMeetings(
            @RequestParam Long residenceId) {
        return ResponseEntity.ok(dashboardService.getUpcomingMeetings(residenceId));
    }

}

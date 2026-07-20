package com.example.solimus.controllers;

import com.example.solimus.dtos.owner.dashboard.NotificationListResponseDTO;
import com.example.solimus.dtos.owner.dashboard.OwnerDashboardHeaderDTO;
import com.example.solimus.dtos.owner.dashboard.OwnerDashboardKpiDTO;
import com.example.solimus.dtos.owner.dashboard.OwnerPendingChargeDTO;
import com.example.solimus.dtos.owner.dashboard.OwnerPropertySelectorDTO;
import com.example.solimus.dtos.owner.meeting.OwnerMeetingCardDTO;
import com.example.solimus.services.owner.dashboard.CoOwnerDashboardService;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "En-tête du dashboard (prénom, photo, compteur de notifications non lues)")
    @GetMapping("/dashboard/header")
    public ResponseEntity<OwnerDashboardHeaderDTO> getDashboardHeader() {
        return ResponseEntity.ok(dashboardService.getDashboardHeader());
    }

    @Operation(summary = "Liste paginée des notifications du copropriétaire connecté")
    @GetMapping("/dashboard/notifications")
    public ResponseEntity<NotificationListResponseDTO> getMyNotifications(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ResponseEntity.ok(dashboardService.getMyNotifications(page, size));
    }

    @Operation(summary = "Marque toutes les notifications du copropriétaire connecté comme lues")
    @PatchMapping("/dashboard/notifications/mark-all-read")
    public ResponseEntity<String> markAllNotificationsAsRead() {
        dashboardService.markAllNotificationsAsRead();
        return ResponseEntity.ok("Notifications marquées comme lues");
    }

    @Operation(summary = "KPIs du dashboard (Charge annuelle + Restant à payer) pour une résidence précise")
    @GetMapping("/dashboard/kpis")
    public ResponseEntity<OwnerDashboardKpiDTO> getDashboardKpis(
            @RequestParam Long residenceId) {
        return ResponseEntity.ok(dashboardService.getDashboardKpis(residenceId));
    }

    @Operation(summary = "Charges en attente pour le dashboard (aperçu limité)")
    @GetMapping("/dashboard/pending-charges")
    public ResponseEntity<List<OwnerPendingChargeDTO>> getPendingCharges(
            @RequestParam Long residenceId) {
        return ResponseEntity.ok(dashboardService.getPendingCharges(residenceId));
    }

    @Operation(summary = "Prochaines réunions pour le dashboard (aperçu limité, résidence précise)")
    @GetMapping("/dashboard/upcoming-meetings")
    public ResponseEntity<List<OwnerMeetingCardDTO>> getUpcomingMeetings(
            @RequestParam Long residenceId) {
        return ResponseEntity.ok(dashboardService.getUpcomingMeetings(residenceId));
    }

}

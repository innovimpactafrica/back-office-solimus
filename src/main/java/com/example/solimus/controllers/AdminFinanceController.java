package com.example.solimus.controllers;

import com.example.solimus.dtos.admin.finance.*;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.RevenuePeriod;
import com.example.solimus.enums.SubscriberType;
import com.example.solimus.services.admin.finance.AdminFinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/finance")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Administration - Finances")
public class AdminFinanceController {

    private final AdminFinanceService financeService;

    @Operation(summary = "KPIs de la page \"Finances\" (revenus du mois/année, part Syndic/Prestataire, croissance, paiements en attente, renouvellements)")
    @GetMapping("/kpis")
    public ResponseEntity<FinanceDashboardKpiDTO> getDashboardKpis() {
        return ResponseEntity.ok(financeService.getDashboardKpis());
    }

    @Operation(summary = "Évolution des revenus (Syndic + Prestataire), mensuelle/trimestrielle/annuelle selon la période demandée")
    @GetMapping("/revenue-evolution")
    public ResponseEntity<List<RevenueChartPointDTO>> getRevenueChart(
            @RequestParam RevenuePeriod period,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(financeService.getRevenueChart(period, year));
    }

    @Operation(summary = "Répartition des revenus du mois en cours entre Syndics et Prestataires")
    @GetMapping("/revenue-split")
    public ResponseEntity<RevenueSplitDTO> getRevenueSplit() {
        return ResponseEntity.ok(financeService.getRevenueSplit());
    }

    @Operation(summary = "Revenu cumulé total et nombre d'abonnés actifs, pour chaque formule (Syndic + Prestataire)")
    @GetMapping("/revenue-by-plan")
    public ResponseEntity<List<PlanRevenueDTO>> getRevenueByPlan() {
        return ResponseEntity.ok(financeService.getRevenueByPlan());
    }

    @Operation(summary = "Liste paginée des transactions d'abonnements (Syndic + Prestataire), avec filtres optionnels")
    @GetMapping("/transactions")
    public ResponseEntity<Page<TransactionRowDTO>> getTransactions(
            @RequestParam(required = false) SubscriberType type,
            @RequestParam(required = false) String planName,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(financeService.getTransactions(type, planName, status, page, size));
    }
}

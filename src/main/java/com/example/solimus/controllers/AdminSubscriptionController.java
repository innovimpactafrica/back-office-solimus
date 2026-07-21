package com.example.solimus.controllers;

import com.example.solimus.dtos.admin.subscription.SubscriptionKpiDTO;
import com.example.solimus.services.admin.subscription.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/subscriptions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
@Tag(name = "Administration - Abonnements")
public class AdminSubscriptionController {

    private final PlanService planService;

    @Operation(summary = "KPIs de la page Gestion des abonnements")
    @GetMapping("/kpis")
    public ResponseEntity<SubscriptionKpiDTO> getSubscriptionKpis() {
        return ResponseEntity.ok(planService.getSubscriptionKpis());
    }
}

package com.example.solimus.controllers;

import com.example.solimus.dtos.admin.syndic.CreateSyndicDTO;
import com.example.solimus.dtos.admin.syndic.CreateSyndicResponseDTO;
import com.example.solimus.dtos.admin.syndic.SyndicPlanOptionDTO;
import com.example.solimus.services.admin.syndic.SyndicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/syndics")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Administration - Syndics")
public class AdminSyndicController {

    private final SyndicService syndicService;

    @Operation(summary = "Créer un nouveau syndic avec son abonnement")
    @PostMapping
    public ResponseEntity<CreateSyndicResponseDTO> createSyndic(@RequestBody @Valid CreateSyndicDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(syndicService.createSyndic(dto));
    }

    @Operation(summary = "Lister les formules disponibles pour la création d'un syndic")
    @GetMapping("/plans")
    public List<SyndicPlanOptionDTO> listAvailablePlans() {
        return syndicService.listAvailablePlans();
    }
}
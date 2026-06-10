package com.example.solimus.controllers;

import com.example.solimus.dtos.provider.WithdrawalRequestDTO;
import com.example.solimus.enums.WithdrawalStatus;
import com.example.solimus.services.syndic.SyndicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/syndic")
@RequiredArgsConstructor
@Tag(name = "4.g Syndic - Retraits", description = "Gestion des demandes de retrait des prestataires par le syndic")
public class SyndicWithdrawalController {

    private final SyndicService syndicService;

    @Operation(summary = "Lister les demandes de retrait des prestataires", tags = {"4.g Syndic - Retraits"})
    @GetMapping("/withdrawals")
    public ResponseEntity<List<WithdrawalRequestDTO>> getWithdrawalRequests(
            @RequestParam(required = false) WithdrawalStatus status) {
        return ResponseEntity.ok(syndicService.getWithdrawalRequests(status));
    }

    @Operation(summary = "Confirmer une demande de retrait", tags = {"4.g Syndic - Retraits"})
    @PatchMapping("/withdrawals/{id}/confirm")
    public ResponseEntity<WithdrawalRequestDTO> confirmWithdrawal(@PathVariable Long id) {
        return ResponseEntity.ok(syndicService.confirmWithdrawal(id));
    }

    @Operation(summary = "Refuser une demande de retrait", tags = {"4.g Syndic - Retraits"})
    @PatchMapping("/withdrawals/{id}/reject")
    public ResponseEntity<WithdrawalRequestDTO> rejectWithdrawal(
            @PathVariable Long id,
            @RequestParam(required = false) String motifRefus) {
        return ResponseEntity.ok(syndicService.rejectWithdrawal(id, motifRefus));
    }
}

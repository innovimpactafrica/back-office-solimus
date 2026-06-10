package com.example.solimus.controllers;

import com.example.solimus.dtos.syndic.CreateCoOwnerDTO;
import com.example.solimus.services.syndic.SyndicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/syndic")
@RequiredArgsConstructor
@Tag(name = "4.d Syndic - Copropriétaires", description = "Gestion des copropriétaires par le syndic")
public class SyndicCoOwnerController {

    private final SyndicService syndicService;

    @Operation(summary = "Ajouter un copropriétaire (Workflow OTP)", tags = {"4.d Syndic - Copropriétaires"})
    @PostMapping("/co-owners")
    public ResponseEntity<String> addCoOwner(@RequestBody @Valid CreateCoOwnerDTO dto) {
        syndicService.addCoOwner(dto);
        return ResponseEntity.ok("Copropriétaire ajouté avec succès. Un code d'activation lui a été envoyé par email.");
    }
}

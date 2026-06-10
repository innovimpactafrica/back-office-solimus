package com.example.solimus.controllers;

import com.example.solimus.dtos.residence.PropertyDTO;
import com.example.solimus.services.syndic.SyndicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/syndic")
@RequiredArgsConstructor
@Tag(name = "4.b Syndic - Biens", description = "Gestion des biens par le syndic")
public class SyndicPropertyController {

    private final SyndicService syndicService;

    @Operation(summary = "Assigner un propriétaire à un bien", tags = {"4.b Syndic - Biens"})
    @PostMapping("/properties/{propertyId}/owners/{userId}")
    public ResponseEntity<PropertyDTO> addOwnerToProperty(
            @PathVariable Long propertyId,
            @PathVariable Long userId) {
        return ResponseEntity.ok(syndicService.addOwner(propertyId, userId));
    }
}

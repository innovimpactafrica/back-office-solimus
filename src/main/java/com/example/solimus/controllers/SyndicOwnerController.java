package com.example.solimus.controllers;

import com.example.solimus.dtos.syndic.owner.CreateCoOwnerDTO;
import com.example.solimus.dtos.syndic.owner.PropertySummaryDTO;
import com.example.solimus.dtos.syndic.owner.ResidenceSummaryDTO;
import com.example.solimus.dtos.syndic.owner.CoOwnerListDTO;
import com.example.solimus.dtos.syndic.owner.CoOwnerSearchResultDTO;
import com.example.solimus.dtos.syndic.owner.CoOwnerPropertyAssignmentDTO;
import com.example.solimus.enums.Nationality;
import com.example.solimus.enums.Title;
import com.example.solimus.services.syndic.owner.SyndicOwnerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/syndic")
@RequiredArgsConstructor
@Tag(name = "Syndic - Copropriétaires", description = "Gestion des copropriétaires par le syndic")
public class SyndicOwnerController {

    private final SyndicOwnerService syndicOwnerService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "Ajouter un copropriétaire (Workflow OTP) avec photo", tags = {"Syndic - Copropriétaires"},
            description = "propertiesJson filtre par résidence pour les lots")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping(value = "/co-owners", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> addCoOwner(
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam String email,
            @RequestParam String phone,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) LocalDate birthDate,
            @RequestParam(required = false) String nationality,
            @RequestParam(required = false) String secondaryPhone,
            @RequestParam(required = false) String address,
            @Parameter(
                description = "Liste des affectations de lots au format JSON (optionnel) — groupée par résidence",
                schema = @Schema(
                    type = "string",
                    example = "[{\"residenceId\":1,\"propertyIds\":[3,7]},{\"residenceId\":2,\"propertyIds\":[12]}]"
                )
            )
            @RequestPart(value = "propertiesJson", required = false) String propertiesJson,
            @RequestPart(value = "photo", required = false) MultipartFile photo) throws JsonProcessingException {

        // Parser les affectations de lots si fournies, sinon liste vide
        List<CoOwnerPropertyAssignmentDTO> properties = new ArrayList<>();
        if (propertiesJson != null && !propertiesJson.trim().isEmpty()) {
            properties = objectMapper.readValue(propertiesJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, CoOwnerPropertyAssignmentDTO.class));
        }

        // Construire le DTO à partir des RequestParam + la liste parsée
        CreateCoOwnerDTO dto = CreateCoOwnerDTO.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .phone(phone)
                .title(title != null ? Title.valueOf(title.toUpperCase()) : null)
                .birthDate(birthDate)
                .nationality(nationality != null ? Nationality.valueOf(nationality.toUpperCase()) : null)
                .secondaryPhone(secondaryPhone)
                .address(address)
                .properties(properties)
                .build();

        syndicOwnerService.addCoOwner(dto, photo);
        return ResponseEntity.ok("Copropriétaire ajouté avec succès. Un code d'activation lui a été envoyé par email.");
    }

    @Operation(summary = "Lister les biens disponibles (VACANT) d'une résidence", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/{residenceId}/properties/available")
    public ResponseEntity<List<PropertySummaryDTO>> getAvailableProperties(
            @PathVariable Long residenceId) {
        return ResponseEntity.ok(syndicOwnerService.getAvailableProperties(residenceId));
    }

    @Operation(summary = "Lister les résidences qui ont au moins un bien vacant", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/with-vacant-properties")
    public ResponseEntity<List<ResidenceSummaryDTO>> getResidencesWithVacantProperties() {
        return ResponseEntity.ok(syndicOwnerService.getResidencesWithVacantProperties());
    }

    @Operation(summary = "Lister les copropriétaires (recherche + filtre résidence + pagination)", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/co-owners")
    public ResponseEntity<Page<CoOwnerListDTO>> getCoOwners(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long residenceId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("firstName").ascending());
        return ResponseEntity.ok(syndicOwnerService.getCoOwners(search, residenceId, pageable));
    }

    @Operation(summary = "Autocomplete — rechercher un copropriétaire par nom, email ou téléphone", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/co-owners/search")
    public ResponseEntity<List<CoOwnerSearchResultDTO>> searchCoOwners(
            @RequestParam String q) {
        return ResponseEntity.ok(syndicOwnerService.searchCoOwners(q));
    }

    @Operation(summary = "Lier un copropriétaire existant au syndic connecté", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/co-owners/{id}/link")
    public ResponseEntity<String> linkCoOwner(@PathVariable Long id) {
        syndicOwnerService.linkCoOwner(id);
        return ResponseEntity.ok("Copropriétaire lié avec succès");
    }
}

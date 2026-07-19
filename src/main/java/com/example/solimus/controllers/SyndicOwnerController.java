package com.example.solimus.controllers;

import com.example.solimus.dtos.owner.CoOwnerInterventionsResponseDTO;
import com.example.solimus.dtos.owner.CoOwnerMeetingsDTO;
import com.example.solimus.dtos.owner.CoOwnerResidenceDTO;
import com.example.solimus.dtos.syndic.owner.*;
import com.example.solimus.dtos.syndic.residence.ActivityLogItemDTO;
import com.example.solimus.enums.Nationality;
import com.example.solimus.enums.Title;
import com.example.solimus.services.syndic.owner.SyndicOwnerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
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
            @Parameter(description = "Civilité")
            @RequestParam(required = false) Title title,
            @Parameter(description = "Date de naissance au format jj/mm/aaaa", schema = @Schema(type = "string", example = "14/05/1988"))
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate birthDate,
            @Parameter(description = "Nationalité")
            @RequestParam(required = false) Nationality nationality,
            @RequestParam(required = false) String secondaryPhone,
            @RequestParam(required = false) String address,
            @Parameter(
                description = "Liste des affectations de lots au format JSON (obligatoire) — groupée par résidence",
                schema = @Schema(
                    type = "string",
                    example = "[{\"residenceId\":1,\"propertyIds\":[3,7]},{\"residenceId\":2,\"propertyIds\":[12]}]"
                )
            )
            @RequestPart(value = "propertiesJson", required = true) String propertiesJson,
            @RequestPart(value = "photo", required = false) MultipartFile photo) throws JsonProcessingException {

        // Parser les affectations de lots (obligatoire)
        List<CoOwnerPropertyAssignmentDTO> properties = objectMapper.readValue(propertiesJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, CoOwnerPropertyAssignmentDTO.class));

        // Construire le DTO à partir des RequestParam + la liste parsée
        CreateCoOwnerDTO dto = CreateCoOwnerDTO.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .phone(phone)
                .title(title)
                .birthDate(birthDate)
                .nationality(nationality)
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
    public ResponseEntity<Page<PropertySummaryDTO>> getAvailableProperties(
            @PathVariable Long residenceId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(syndicOwnerService.getAvailableProperties(residenceId, page, size));
    }

    @Operation(summary = "Lister les résidences qui ont au moins un bien vacant", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/with-vacant-properties")
    public ResponseEntity<Page<ResidenceSummaryDTO>> getResidencesWithVacantProperties(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(syndicOwnerService.getResidencesWithVacantProperties(page, size));
    }

    @Operation(summary = "Lister les copropriétaires (recherche + filtre résidence + statut + pagination)", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/co-owners")
    public ResponseEntity<Page<CoOwnerListDTO>> getCoOwners(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long residenceId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(syndicOwnerService.getCoOwners(search, residenceId, status, page, size));
    }

    @Operation(summary = "Détail d'un copropriétaire (en-tête + KPIs)", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/co-owners/{coOwnerId}")
    public ResponseEntity<CoOwnerDetailDTO> getCoOwnerDetail(@PathVariable Long coOwnerId) {
        return ResponseEntity.ok(syndicOwnerService.getCoOwnerDetail(coOwnerId));
    }

    @Operation(summary = "Lister les résidences d'un copropriétaire (pour le filtre finances)", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/co-owners/{coOwnerId}/residences")
    public ResponseEntity<Page<CoOwnerResidenceDTO>> getCoOwnerResidences(
            @PathVariable Long coOwnerId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(syndicOwnerService.getCoOwnerResidences(coOwnerId, page, size));
    }

    @Operation(summary = "Lister les lots d'un copropriétaire (onglet Appartements du détail)", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/co-owners/{coOwnerId}/properties")
    public ResponseEntity<Page<CoOwnerPropertyItemDTO>> getCoOwnerProperties(
            @PathVariable Long coOwnerId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(syndicOwnerService.getCoOwnerProperties(coOwnerId, page, size));
    }

    @Operation(summary = "Finances d'un copropriétaire pour une résidence (onglet Finances du détail)", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/co-owners/{coOwnerId}/finances")
    public ResponseEntity<CoOwnerFinancesDTO> getCoOwnerFinances(
            @PathVariable Long coOwnerId,
            @RequestParam Long residenceId) {
        return ResponseEntity.ok(syndicOwnerService.getCoOwnerFinances(coOwnerId, residenceId));
    }

    @Operation(summary = "Historique des paiements d'un copropriétaire (onglet Paiements du détail)", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/co-owners/{coOwnerId}/payments")
    public ResponseEntity<Page<CoOwnerPaymentItemDTO>> getCoOwnerPayments(
            @PathVariable Long coOwnerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(syndicOwnerService.getCoOwnerPayments(coOwnerId, status, page, size));
    }

    @Operation(summary = "Assemblées Générales d'un copropriétaire (onglet AG du détail)", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/co-owners/{coOwnerId}/meetings")
    public ResponseEntity<CoOwnerMeetingsDTO> getCoOwnerMeetings(
            @PathVariable Long coOwnerId,
            @RequestParam(required = false) Long residenceId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(syndicOwnerService.getCoOwnerMeetings(coOwnerId, residenceId, type, year, page, size));
    }

    @Operation(summary = "Travaux d'un copropriétaire (onglet Travaux du détail)", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/co-owners/{coOwnerId}/interventions")
    public ResponseEntity<CoOwnerInterventionsResponseDTO> getCoOwnerInterventions(@PathVariable Long coOwnerId) {
        return ResponseEntity.ok(syndicOwnerService.getCoOwnerInterventions(coOwnerId));
    }

    @Operation(summary = "Documents d'un copropriétaire (manuel + AG + charges exceptionnelles, fusionnés)", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/co-owners/{coOwnerId}/documents")
    public ResponseEntity<CoOwnerDocumentUnifiedListResponseDTO> getCoOwnerDocuments(
            @PathVariable Long coOwnerId,
            @RequestParam(required = false) String search,
            @Parameter(description = "Catégorie exacte. Depuis MeetingDocument : CONVOCATION, FINANCIAL, REPORT, PV_AG, OTHER. " +
                    "Depuis ExceptionalCallDocument : Charges. " +
                    "Depuis CoOwnerDocument (coffre manuel) : PROPERTY_TITLE, CONTRACT, IDENTITY_DOCUMENT, PAYMENT_RECEIPT")
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {

        return ResponseEntity.ok(syndicOwnerService.getCoOwnerDocuments(
                coOwnerId, search, category, page, size));
    }

    @Operation(summary = "Activité récente d'un copropriétaire (panneau Activité Récente du détail)", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/co-owners/{coOwnerId}/activity-log")
    public ResponseEntity<Page<ActivityLogItemDTO>> getCoOwnerActivityLog(
            @PathVariable Long coOwnerId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "5") Integer size) {
        return ResponseEntity.ok(syndicOwnerService.getCoOwnerActivityLog(coOwnerId, page, size));
    }

    @Operation(summary = "Autocomplete — rechercher un copropriétaire par nom, email ou téléphone", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/co-owners/search")
    public ResponseEntity<Page<CoOwnerSearchResultDTO>> searchCoOwners(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "5") Integer size) {
        return ResponseEntity.ok(syndicOwnerService.searchCoOwners(q, page, size));
    }

    @Operation(summary = "Lier un copropriétaire existant au syndic connecté", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/co-owners/{id}/link")
    public ResponseEntity<String> linkCoOwner(@PathVariable Long id) {
        syndicOwnerService.linkCoOwner(id);
        return ResponseEntity.ok("Copropriétaire lié avec succès");
    }

    @Operation(summary = "Mettre à jour partiellement un copropriétaire", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PatchMapping("/co-owners/{id}")
    public ResponseEntity<Void> updateCoOwner(
            @PathVariable Long id,
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @Parameter(description = "Civilité")
            @RequestParam(required = false) Title title,
            @Parameter(description = "Date de naissance au format jj/mm/aaaa", schema = @Schema(type = "string", example = "14/05/1988"))
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate birthDate,
            @Parameter(description = "Nationalité")
            @RequestParam(required = false) Nationality nationality,
            @RequestParam(required = false) String secondaryPhone,
            @RequestParam(required = false) String address) {
        syndicOwnerService.updateCoOwner(id, firstName, lastName, email, phone, title, birthDate, nationality, secondaryPhone, address);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Supprimer un copropriétaire et libérer ses lots", tags = {"Syndic - Copropriétaires"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @DeleteMapping("/co-owners/{id}")
    public ResponseEntity<Void> deleteCoOwner(@PathVariable Long id) {
        syndicOwnerService.deleteCoOwner(id);
        return ResponseEntity.noContent().build();
    }
}

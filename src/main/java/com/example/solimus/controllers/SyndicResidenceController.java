package com.example.solimus.controllers;

import com.example.solimus.dtos.syndic.residence.*;
import com.example.solimus.dtos.syndic.settings.FacilityTypeDTO;
import com.example.solimus.services.syndic.residence.SyndicResidenceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/syndic")
@RequiredArgsConstructor
@Tag(name = "Syndic - Résidences", description = "Gestion des résidences par le syndic")
public class SyndicResidenceController {

    private final SyndicResidenceService residenceService;
    private final ObjectMapper objectMapper;



    // =========================================================================
    // ÉTAPE 1 — RÉSIDENCE
    // =========================================================================

    @Operation(summary = "Créer une nouvelle résidence (Étape 1)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping(value = "/residences", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResidenceDTO> createResidence(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam String fullAddress,
            @RequestParam String city,
            @RequestParam String country,
            @RequestParam BigDecimal latitude,
            @RequestParam BigDecimal longitude,
            @RequestParam(required = false) String constructionDate,
            @RequestParam(required = false) String renovationDate,
            @Parameter(
                description = "Liste des contacts clés au format JSON (optionnel)",
                schema = @Schema(
                    type = "string",
                    example = "[{\"fullName\":\"Seydina Fall\",\"phone\":\"+221774569909\"}]"
                )
            )
            @RequestPart(value = "contactsJson", required = false) String contactsJson,
            @RequestPart(value = "photo", required = true) MultipartFile photo) throws JsonProcessingException {

        // Parser les contacts si fournis, sinon liste vide
        List<ContactInputDTO> contacts = new ArrayList<>();
        if (contactsJson != null && !contactsJson.trim().isEmpty()) {
            contacts = objectMapper.readValue(contactsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ContactInputDTO.class));
        }

        // Parser les dates si fournies
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDate parsedConstructionDate = (constructionDate != null && !constructionDate.trim().isEmpty())
                ? LocalDate.parse(constructionDate, formatter) : null;
        LocalDate parsedRenovationDate = (renovationDate != null && !renovationDate.trim().isEmpty())
                ? LocalDate.parse(renovationDate, formatter) : null;

        // Construire le DTO
        CreateResidenceDTO dto = CreateResidenceDTO.builder()
                .name(name)
                .description(description)
                .fullAddress(fullAddress)
                .city(city)
                .country(country)
                .latitude(latitude)
                .longitude(longitude)
                .constructionDate(parsedConstructionDate)
                .renovationDate(parsedRenovationDate)
                .contacts(contacts)
                .build();

        ResidenceDTO result = residenceService.createResidenceComplete(dto, photo);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // =========================================================================
    // ÉTAPE 2 — LOTS
    // =========================================================================

    @Operation(summary = "Ajouter un ou plusieurs lots/appartements (Étape 2)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/residences/{id}/properties")
    public ResponseEntity<List<PropertyDTO>> addProperties(
            @PathVariable Long id,
            @RequestBody @Valid List<AddPropertyDTO> properties) {
        return ResponseEntity.status(HttpStatus.CREATED).body(residenceService.addProperties(id, properties));
    }

    @Operation(summary = "Modifier un lot/appartement (Étape 2)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PutMapping("/residences/{id}/properties/{propertyId}")
    public ResponseEntity<PropertyDTO> updateProperty(
            @PathVariable Long id,
            @PathVariable Long propertyId,
            @RequestBody @Valid UpdatePropertyDTO dto) {
        return ResponseEntity.ok(residenceService.updateProperty(id, propertyId, dto));
    }

    @Operation(summary = "Supprimer un lot/appartement (Étape 2)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @DeleteMapping("/residences/{id}/properties/{propertyId}")
    public ResponseEntity<Void> deleteProperty(
            @PathVariable Long id,
            @PathVariable Long propertyId) {
        residenceService.deleteProperty(id, propertyId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lister les lots d'une résidence (paginé) (Étape 2)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/{id}/properties")
    public ResponseEntity<Page<PropertyListDTO>> getPropertiesPaginated(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "5") Integer size) {
        return ResponseEntity.ok(residenceService.getPropertiesPaginated(id, page, size));
    }

    @Operation(summary = "Lister les copropriétaires pour affecter un lot (Étape 2)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/properties/co-owners")
    public ResponseEntity<List<CoOwnerSelectionDTO>> searchCoOwnersForSelection(
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(residenceService.searchCoOwnersForSelection(search));
    }

    @Operation(summary = "Lister tous les types de biens (dropdown) (Étape 2)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/property-types")
    public ResponseEntity<List<PropertyTypeDTO>> getAllPropertyTypes() {
        return ResponseEntity.ok(residenceService.getAllPropertyTypes());
    }

    // =========================================================================
    // ÉTAPE 3 — BIENS COMMUNS
    // =========================================================================
    @Operation(summary = "Lister les types d'équipements avec leurs champs (Étape 3)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/facility-types")
    public ResponseEntity<List<FacilityTypeDTO>> getFacilityTypes() {
        return ResponseEntity.ok(residenceService.getFacilityTypes());
    }

    @Operation(summary = "Mettre à jour les options de sécurité d'une résidence (Étape 3)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PutMapping("/residences/{id}/security-features")
    public ResponseEntity<Void> updateSecurityFeatures(
            @PathVariable Long id,
            @RequestBody UpdateSecurityFeaturesDTO dto) {
        residenceService.updateSecurityFeatures(id, dto);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Sauvegarder l'étape 3 complète (équipements + sécurité) (Étape 3)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/residences/{id}/step3")
    public ResponseEntity<Void> saveStep3(
            @PathVariable Long id,
            @RequestBody Step3DTO dto) {
        residenceService.saveStep3(id, dto);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Modifier les informations générales d'une résidence (mise à jour partielle)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PatchMapping(value = "/residences/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updateResidence(
            @PathVariable Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String fullAddress,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) String constructionDate,
            @RequestParam(required = false) String renovationDate,
            @Parameter(
                description = "Liste des contacts clés au format JSON (optionnel)",
                schema = @Schema(
                    type = "string",
                    example = "[{\"fullName\":\"Seydina Fall\",\"role\":\"Gardien\",\"phone\":\"+221774569909\"}]"
                )
            )
            @RequestPart(value = "contactsJson", required = false) String contactsJson,
            @RequestPart(value = "photo", required = false) MultipartFile photo) throws JsonProcessingException {

        // Parser les contacts si fournis, sinon liste vide
        List<ContactInputDTO> contacts = new ArrayList<>();
        if (contactsJson != null && !contactsJson.trim().isEmpty()) {
            contacts = objectMapper.readValue(contactsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ContactInputDTO.class));
        }

        // Parser les dates si fournies
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDate parsedConstructionDate = (constructionDate != null && !constructionDate.trim().isEmpty())
                ? LocalDate.parse(constructionDate, formatter) : null;
        LocalDate parsedRenovationDate = (renovationDate != null && !renovationDate.trim().isEmpty())
                ? LocalDate.parse(renovationDate, formatter) : null;

        // Construire le DTO
        CreateResidenceDTO dto = CreateResidenceDTO.builder()
                .name(name)
                .description(description)
                .fullAddress(fullAddress)
                .city(city)
                .country(country)
                .latitude(latitude)
                .longitude(longitude)
                .constructionDate(parsedConstructionDate)
                .renovationDate(parsedRenovationDate)
                .contacts(contacts)
                .build();

        residenceService.updateResidence(id, dto, photo);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // DASHBOARD RÉSIDENCES
    // =========================================================================

    @Operation(summary = "Statistiques globales du dashboard résidences", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/dashboard/stats")
    public ResponseEntity<ResidenceDashboardStatsDTO> getDashboardStats() {
        return ResponseEntity.ok(residenceService.getDashboardStats());
    }

    @Operation(summary = "Liste paginée et filtrée des résidences (cartes)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/list")
    public ResponseEntity<Page<ResidenceCardDTO>> getResidencesPaginated(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "12") Integer size) {
        return ResponseEntity.ok(residenceService.getResidencesPaginated(search, city, status, page, size));
    }

    @Operation(summary = "Lister les options de sécurité disponibles (Étape 3)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/security-features")
    public ResponseEntity<List<SecurityFeatureLabelDTO>> getSecurityFeatures() {
        return ResponseEntity.ok(residenceService.getSecurityFeatures());
    }

    // =========================================================================
    // ONGLET 1
    // =========================================================================
    @Operation(summary = "Statistiques du bandeau d'indicateurs d'une résidence", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/{id}/stats")
    public ResponseEntity<ResidenceHeaderStatsDTO> getResidenceStats(@PathVariable Long id) {
        return ResponseEntity.ok(residenceService.getResidenceStats(id));
    }

    @Operation(summary = "Contenu de l'onglet Vue générale d'une résidence", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/{id}")
    public ResponseEntity<ResidenceDetailDTO> getResidenceGeneralView(@PathVariable Long id) {
        return ResponseEntity.ok(residenceService.getResidenceGeneralView(id));
    }

    // =========================================================================
    // ONGLET 2
    // =========================================================================
    @Operation(summary = "Lister les lots d'une résidence avec filtres (onglet Appartements)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/{residenceId}/properties/list")
    public ResponseEntity<Page<PropertyListItemDTO>> getPropertiesPaginatedWithFilters(
            @PathVariable Long residenceId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer floor,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "12") Integer size) {
        return ResponseEntity.ok(residenceService.getPropertiesPaginatedWithFilters(
                residenceId, search, floor, status, page, size));
    }

    // =========================================================================
    // ONGLET 3
    // =========================================================================
    @Operation(summary = "Lister les équipements communs d'une résidence avec filtres (onglet Biens communs)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/{residenceId}/common-facilities")
    public ResponseEntity<List<CommonFacilityListItemDTO>> getCommonFacilitiesWithFilters(
            @PathVariable Long residenceId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(residenceService.getCommonFacilitiesWithFilters(
                residenceId, search, status));
    }

    @Operation(summary = "Détail d'un équipement commun (onglet Biens communs)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/{residenceId}/common-facilities/{facilityId}")
    public ResponseEntity<CommonFacilityDetailDTO> getCommonFacilityDetail(
            @PathVariable Long residenceId,
            @PathVariable Long facilityId) {
        return ResponseEntity.ok(residenceService.getCommonFacilityDetail(residenceId, facilityId));
    }

    @Operation(summary = "Kanban des interventions (onglet Travaux)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/{residenceId}/interventions/kanban")
    public ResponseEntity<InterventionKanbanResponseDTO> getInterventionsKanban(
            @PathVariable Long residenceId) {
        return ResponseEntity.ok(residenceService.getInterventionsKanban(residenceId));
    }

    // =========================================================================
    // ONGLET 4
    // =========================================================================
    @Operation(summary = "Évolution mensuelle des paiements collectés (onglet Finances)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/{residenceId}/finances/payments-evolution")
    public ResponseEntity<List<MonthlyPaymentDTO>> getMonthlyPaymentsEvolution(
            @PathVariable Long residenceId,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(residenceService.getMonthlyPaymentsEvolution(residenceId, year));
    }

    @Operation(summary = "Répartition des vraies dépenses par catégorie (onglet Finances)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/{residenceId}/finances/expenses-breakdown")
    public ResponseEntity<ExpenseBreakdownDTO> getExpensesBreakdown(
            @PathVariable Long residenceId,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(residenceService.getExpensesBreakdown(residenceId, year));
    }

    @Operation(summary = "Liste des appels de charges par copropriétaire (onglet Finances)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/{residenceId}/finances/charge-calls-summary")
    public ResponseEntity<List<ChargeCallItemSummaryDTO>> getChargeCallsSummary(
            @PathVariable Long residenceId) {
        return ResponseEntity.ok(residenceService.getChargeCallsSummary(residenceId));
    }

    @Operation(summary = "Liste des transactions récentes du wallet (onglet Finances)", tags = {"Syndic - Résidences"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/{residenceId}/wallet/transactions/recent")
    public ResponseEntity<List<WalletTransactionDTO>> getRecentWalletTransactions(
            @PathVariable Long residenceId,
            @RequestParam(required = false, defaultValue = "5") Integer limit) {
        return ResponseEntity.ok(residenceService.getRecentWalletTransactions(residenceId, limit));
    }

    @Operation(summary = "Journal d'activité d'une résidence (panneau Activité Récente)", tags = {"Syndic - Résidences"},
            description = "scope=interventions filtre par 'INTERVENTION' pour avoir les travaux")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @GetMapping("/residences/{residenceId}/activity-log")
    public ResponseEntity<Page<ActivityLogItemDTO>> getActivityLog(
            @PathVariable Long residenceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(required = false) String scope) {
        return ResponseEntity.ok(residenceService.getActivityLog(residenceId, page, size, scope));
    }


}

package com.example.solimus.controllers;

import com.example.solimus.dtos.charge.ChargeResponseDTO;
import com.example.solimus.dtos.charge.CreateChargeDTO;
import com.example.solimus.dtos.charge.CreateChargeLineDTO;
import com.example.solimus.dtos.intervention.CreateInterventionRequestDTO;
import com.example.solimus.dtos.intervention.InterventionRequestDTO;
import com.example.solimus.dtos.intervention.NearbyProviderDTO;
import com.example.solimus.dtos.intervention.SyndicQuoteDTO;
import com.example.solimus.dtos.property.CreatePropertyDTO;
import com.example.solimus.dtos.property.PropertyDTO;
import com.example.solimus.dtos.residence.CreateResidenceDTO;
import com.example.solimus.dtos.residence.ResidenceDTO;
import com.example.solimus.dtos.syndic.CreateCoOwnerDTO;
import com.example.solimus.dtos.syndic.PaymentResponseDTO;
import com.example.solimus.dtos.syndic.PayerAcompteDTO;
import com.example.solimus.services.minio.MinioService;
import com.example.solimus.services.syndic.SyndicService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/syndic")
@RequiredArgsConstructor
@Tag(name = "Syndic API", description = "Endpoints réservés aux syndics pour la gestion des résidences et interventions")
public class SyndicController {

    private final SyndicService syndicService;
    private final MinioService minioService;

    // --- Gestion Résidences ---

    @Operation(summary = "Lister mes résidences")
    @GetMapping("/residences")
    public ResponseEntity<List<ResidenceDTO>> getMyResidences() {
        return ResponseEntity.ok(syndicService.getMyResidences());
    }

    @Operation(summary = "Créer une nouvelle résidence")
    @PostMapping("/residences")
    public ResponseEntity<ResidenceDTO> createResidence(@RequestBody @Valid CreateResidenceDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(syndicService.createResidence(dto));
    }

    @Operation(summary = "Lister les biens d'une résidence")
    @GetMapping("/residences/{id}/properties")
    public ResponseEntity<List<PropertyDTO>> getPropertiesByResidence(@PathVariable Long id) {
        return ResponseEntity.ok(syndicService.getPropertiesByResidence(id));
    }

    // --- Gestion Biens (Properties) ---

    @Operation(summary = "Créer un nouveau bien (Appartement/Local)")
    @PostMapping("/properties")
    public ResponseEntity<PropertyDTO> createProperty(@RequestBody @Valid CreatePropertyDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(syndicService.createProperty(dto));
    }

    @Operation(summary = "Assigner un propriétaire à un bien")
    @PostMapping("/properties/{propertyId}/owners/{userId}")
    public ResponseEntity<PropertyDTO> addOwnerToProperty(
            @PathVariable Long propertyId,
            @PathVariable Long userId) {
        return ResponseEntity.ok(syndicService.addOwner(propertyId, userId));
    }

    // --- Gestion Interventions ---

    @Operation(summary = "Trouver les prestataires proches (Rayon 30km + Spécialité)")
    @GetMapping("/nearby-providers")
    public ResponseEntity<List<NearbyProviderDTO>> getNearbyProviders(
            @RequestParam Long residenceId,
            @RequestParam Long specialtyId) {
        return ResponseEntity.ok(syndicService.findNearbyProviders(residenceId, specialtyId));
    }

    @Operation(summary = "Créer une demande d'intervention (Avec upload Minio)")
    @PostMapping(value = "/interventions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createIntervention(
            @Parameter(description = "Titre court de l'intervention (ex: Fuite d'eau)")
            @RequestPart("title") String title,

            @Parameter(description = "Description détaillée du problème")
            @RequestPart("description") String description,

            @Parameter(description = "ID de la résidence concernée")
            @RequestPart("residenceId") String residenceId,

            @Parameter(description = "ID du bien (appartement/local) concerné")
            @RequestPart("propertyId") String propertyId,

            @Parameter(description = "ID de la spécialité requise (Plomberie, Électricité, etc.)")
            @RequestPart("specialtyId") String specialtyId,

            @Parameter(description = "Photos du problème (JPG, PNG uniquement)")
            @RequestPart(value = "photos", required = false) MultipartFile[] photos) {

        try {
            // Conversion manuelle car @RequestPart avec des types simples peut être capricieux selon le client
            Long resId = Long.parseLong(residenceId);
            Long propId = Long.parseLong(propertyId);
            Long specId = Long.parseLong(specialtyId);
            List<String> photoNames = new ArrayList<>();
            if (photos != null && photos.length > 0) {
                for (MultipartFile photo : photos) {
                    if (photo.isEmpty()) continue;

                    String originalFilename = photo.getOriginalFilename();
                    if (originalFilename != null) {
                        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
                        if (!extension.equals("jpg") && !extension.equals("jpeg") && !extension.equals("png")) {
                            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                    .body("Format d'image non supporté. Formats acceptés: JPG, PNG");
                        }
                    }
                    
                    String uploadedFileName = minioService.uploadFile(photo, "interventions");
                    if (uploadedFileName != null) {
                        photoNames.add(uploadedFileName);
                    }
                }
            }

            CreateInterventionRequestDTO dto = new CreateInterventionRequestDTO();
            dto.setTitle(title);
            dto.setDescription(description);
            dto.setResidenceId(resId);
            dto.setPropertyId(propId);
            dto.setSpecialtyId(specId);
            dto.setPhotoUrls(photoNames);

            return ResponseEntity.status(HttpStatus.CREATED).body(syndicService.createInterventionRequest(dto));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la création de la demande : " + e.getMessage());
        }
    }

    @Operation(summary = "Accepter un devis et valider le prestataire")
    @PostMapping("/interventions/{requestId}/accept-quote/{quoteId}")
    public ResponseEntity<String> acceptQuote(
            @PathVariable Long requestId,
            @PathVariable Long quoteId) {
        syndicService.acceptQuote(requestId, quoteId);
        return ResponseEntity.ok("Devis accepté avec succès. Le prestataire a été notifié.");
    }

    @Operation(summary = "Lister les devis reçus pour une demande")
    @GetMapping("/interventions/{requestId}/quotes")
    public ResponseEntity<List<SyndicQuoteDTO>> getQuotesByIntervention(
            @PathVariable Long requestId) {
        return ResponseEntity.ok(syndicService.getQuotesByInterventionRequest(requestId));
    }

    @Operation(summary = "Lister mes demandes d'intervention")
    @GetMapping("/interventions")
    public ResponseEntity<List<InterventionRequestDTO>> getMyInterventions() {
        return ResponseEntity.ok(syndicService.getMyInterventionRequests());
    }

    @Operation(summary = "Prendre en charge une intervention et diffuser aux prestataires")
    @PostMapping("/interventions/{requestId}/assign")
    public ResponseEntity<InterventionRequestDTO> assignIntervention(@PathVariable Long requestId) {
        return ResponseEntity.ok(syndicService.assignIntervention(requestId));
    }

    // --- Gestion Copropriétaires ---

    @Operation(summary = "Ajouter un copropriétaire (Workflow OTP)")
    @PostMapping("/co-owners")
    public ResponseEntity<String> addCoOwner(@RequestBody @Valid CreateCoOwnerDTO dto) {
        syndicService.addCoOwner(dto);
        return ResponseEntity.ok("Copropriétaire ajouté avec succès. Un code d'activation lui a été envoyé par email.");
    }

    // =========================================================================
    // CHARGES — gestion des charges de copropriété
    // =========================================================================

    @Operation(summary = "Créer une charge pour une résidence (Avec upload Minio)")
    @PostMapping(value = "/charges", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createCharge(
            @Parameter(description = "Titre de la charge")
            @RequestPart("title") String title,

            @Parameter(description = "Description de la charge")
            @RequestPart(value = "description", required = false) String description,

            @Parameter(description = "Type de charge (CHARGES_COURANTES, CHARGES_SPECIALES)")
            @RequestPart("type") String type,

            @Parameter(description = "Montant total de la charge")
            @RequestPart("totalAmount") String totalAmount,

            @Parameter(description = "Période (ex: Juin 2026)")
            @RequestPart(value = "period", required = false) String period,

            @Parameter(description = "Date d'échéance (format: yyyy-MM-dd)")
            @RequestPart(value = "dueDate", required = false) String dueDate,

            @Parameter(description = "ID de la résidence")
            @RequestPart("residenceId") String residenceId,

            @Parameter(description = "Lignes de répartition (JSON)")
            @RequestPart(value = "lines", required = false) String lines,

            @Parameter(description = "Documents joints (PDF, JPG, PNG)")
            @RequestPart(value = "documents", required = false) MultipartFile[] documents) {

        try {
            CreateChargeDTO dto = new CreateChargeDTO();
            dto.setTitle(title);
            dto.setDescription(description);
            dto.setType(com.example.solimus.enums.ChargeType.valueOf(type));
            dto.setTotalAmount(new java.math.BigDecimal(totalAmount));
            dto.setPeriod(period);
            if (dueDate != null && !dueDate.isEmpty()) {
                dto.setDueDate(java.time.LocalDate.parse(dueDate));
            }
            dto.setResidenceId(Long.parseLong(residenceId));

            // Gestion des lignes de répartition
            if (lines != null && !lines.isEmpty()) {
                ObjectMapper mapper = new ObjectMapper();
                mapper.findAndRegisterModules();

                // Nettoyer la chaîne si elle contient "lines": ou lines: prefix
                String cleanedLines = lines.trim();
                if (cleanedLines.startsWith("\"lines\":")) {
                    cleanedLines = cleanedLines.substring(8).trim();
                } else if (cleanedLines.startsWith("lines:")) {
                    cleanedLines = cleanedLines.substring(6).trim();
                }

                java.util.List<CreateChargeLineDTO> lineList = mapper.readValue(cleanedLines,
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.List<CreateChargeLineDTO>>() {});
                dto.setLines(lineList);
            }

            // Upload des documents
            java.util.List<String> documentUrls = new ArrayList<>();
            if (documents != null && documents.length > 0) {
                for (MultipartFile document : documents) {
                    if (document.isEmpty()) continue;

                    String originalFilename = document.getOriginalFilename();
                    if (originalFilename != null) {
                        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
                        if (!extension.equals("pdf") && !extension.equals("jpg") && !extension.equals("jpeg") && !extension.equals("png")) {
                            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                    .body("Format de document non supporté. Formats acceptés: PDF, JPG, PNG");
                        }
                    }

                    String uploadedFileName = minioService.uploadFile(document, "charges");
                    if (uploadedFileName != null) {
                        documentUrls.add(uploadedFileName);
                    }
                }
            }
            dto.setDocumentUrls(documentUrls);

            return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(syndicService.createCharge(dto));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la création de la charge : " + e.getMessage());
        }
    }

    @Operation(summary = "Lister les charges d'une résidence")
    @GetMapping("/charges/residence/{residenceId}")
    public ResponseEntity<List<ChargeResponseDTO>> getChargesByResidence(
            @PathVariable Long residenceId) {
        return ResponseEntity.ok(
            syndicService.getChargesByResidence(residenceId));
    }

    @Operation(summary = "Supprimer une charge")
    @DeleteMapping("/charges/{chargeId}")
    public ResponseEntity<String> deleteCharge(@PathVariable Long chargeId) {
        syndicService.deleteCharge(chargeId);
        return ResponseEntity.ok("Charge supprimée avec succès");
    }

    // =========================================================================
    // PAIEMENTS
    // =========================================================================

    @Operation(summary = "Payer un acompte pour une intervention validée")
    @PostMapping("/interventions/{id}/payer-acompte")
    public ResponseEntity<PaymentResponseDTO> payerAcompte(
            @PathVariable Long id,
            @RequestBody @Valid PayerAcompteDTO dto) {
        return ResponseEntity.ok(syndicService.payerAcompte(id, dto));
    }

    @Operation(summary = "Valider les travaux et payer le solde")
    @PostMapping("/interventions/{id}/valider-solde")
    public ResponseEntity<PaymentResponseDTO> validerEtPayerSolde(
            @PathVariable Long id,
            @RequestBody @Valid com.example.solimus.dtos.syndic.ValiderTravauxDTO dto) {
        return ResponseEntity.ok(syndicService.validerEtPayerSolde(id, dto));
    }
}

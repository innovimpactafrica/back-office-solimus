package com.example.solimus.controllers;

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

            @Parameter(description = "Liste des IDs des prestataires sélectionnés par le syndic")
            @RequestPart("targetProviderIds") String targetProviderIds,

            @Parameter(description = "Photos du problème (JPG, PNG uniquement)")
            @RequestPart(value = "photos", required = false) MultipartFile[] photos) {
        
        try {
            // Conversion manuelle car @RequestPart avec des types simples peut être capricieux selon le client
            Long resId = Long.parseLong(residenceId);
            Long propId = Long.parseLong(propertyId);
            Long specId = Long.parseLong(specialtyId);
            
            // Pour la liste d'IDs, on s'attend à une chaîne séparée par des virgules ou du JSON
            List<Long> providerIds = java.util.Arrays.stream(targetProviderIds.split(","))
                    .map(String::trim)
                    .map(Long::parseLong)
                    .collect(java.util.stream.Collectors.toList());
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
            dto.setTargetProviderIds(providerIds);
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

    // --- Gestion Copropriétaires ---

    @Operation(summary = "Ajouter un copropriétaire (Workflow OTP)")
    @PostMapping("/co-owners")
    public ResponseEntity<String> addCoOwner(@RequestBody @Valid CreateCoOwnerDTO dto) {
        syndicService.addCoOwner(dto);
        return ResponseEntity.ok("Copropriétaire ajouté avec succès. Un code d'activation lui a été envoyé par email.");
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

package com.example.solimus.controllers;

import com.example.solimus.dtos.syndic.meeting.*;
import com.example.solimus.dtos.syndic.residence.ResidenceCardDTO;
import com.example.solimus.enums.MeetingDocumentType;
import com.example.solimus.enums.MeetingStatus;
import com.example.solimus.enums.MeetingType;
import com.example.solimus.services.syndic.residence.SyndicResidenceService;
import com.example.solimus.services.syndic.syndicAG.SyndicMeetingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/syndic/ag")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_SYNDIC')")
@Tag(name = "Syndic - Assemblées Générales")
public class SyndicAGController {

    private final SyndicMeetingService syndicMeetingService;
    private final ObjectMapper objectMapper;
    private final SyndicResidenceService syndicResidenceService;

    @GetMapping("/residences")
    @Operation(summary = "Lister les résidences du syndic")
    public ResponseEntity<Page<ResidenceCardDTO>> getResidences(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(syndicResidenceService.getResidencesPaginated(null, null, null, page, size));
    }

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Créer une nouvelle assemblée générale")
    public ResponseEntity<String> createMeeting(
            @RequestParam Long residenceId,
            @RequestParam String title,
            @RequestParam(required = false) MeetingType type,
            @Parameter(
                description = "Date de la réunion",
                schema = @Schema(type = "string", format = "date", example = "2026-12-25")
            )
            @RequestParam LocalDate meetingDate,
            @Parameter(
                description = "Heure de début",
                schema = @Schema(type = "string", format = "time", example = "09:30:00")
            )
            @RequestParam LocalTime startTime,
            @Parameter(
                description = "Heure de fin",
                schema = @Schema(type = "string", format = "time", example = "16:00:00")
            )
            @RequestParam(required = false) LocalTime endTime,
            @Parameter(description = "Lieu de la réunion", example = "Salle des fêtes")
            @RequestParam(required = false) String location,
            @Parameter(
                description = "Date d'envoi de la convocation",
                schema = @Schema(type = "string", format = "date", example = "2026-12-20")
            )
            @RequestParam(required = false) LocalDate convocationSentDate,
            @Parameter(description = "Message de la convocation", example = "Convocation à l'assemblée générale")
            @RequestParam(required = false) String convocationMessage,
            @Parameter(description = "Envoyer par email", example = "true")
            @RequestParam(required = false) Boolean sendByEmail,
            @Parameter(description = "Envoyer par notification plateforme", example = "true")
            @RequestParam(required = false) Boolean sendByPlatformNotification,
            @Parameter(description = "Envoyer par SMS", example = "false")
            @RequestParam(required = false) Boolean sendBySms,
            @Parameter(
                description = "Liste des points de l'ordre du jour au format JSON (optionnel)",
                schema = @Schema(
                    type = "string",
                    example = "[{\"title\":\"Point 1\",\"description\":\"Description\",\"requiresResolution\":false}]"
                )
            )
            @RequestPart(value = "agendaItemsJson", required = false) String agendaItemsJson,
            @Parameter(description = "Publier immédiatement la réunion", example = "false")
            @RequestParam Boolean publish,
            @RequestPart(value = "documents", required = false) List<MultipartFile> documents) throws JsonProcessingException {

        // Parser les agenda items si fournis, sinon liste vide
        List<AgendaItemDTO> agendaItems = new ArrayList<>();
        if (agendaItemsJson != null && !agendaItemsJson.trim().isEmpty()) {
            agendaItems = objectMapper.readValue(agendaItemsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, AgendaItemDTO.class));
        }

        // Validation : la date de convocation ne doit pas être dans le passé
        if (convocationSentDate != null && convocationSentDate.isBefore(java.time.LocalDate.now())) {
            throw new IllegalArgumentException("La date d'envoi de la convocation ne peut pas être dans le passé");
        }

        // Construire le DTO
        CreateMeetingDTO dto = CreateMeetingDTO.builder()
                .residenceId(residenceId)
                .title(title)
                .type(type)
                .meetingDate(meetingDate)
                .startTime(startTime)
                .endTime(endTime)
                .location(location)
                .convocationSentDate(convocationSentDate)
                .convocationMessage(convocationMessage)
                .sendByEmail(sendByEmail)
                .sendByPlatformNotification(sendByPlatformNotification)
                .sendBySms(sendBySms)
                .agendaItems(agendaItems)
                .publish(publish)
                .build();

        syndicMeetingService.createMeeting(dto, documents);
        return ResponseEntity.ok("Réunion ajoutée avec succès");
    }

    @GetMapping("/list")
    @Operation(summary = "Lister les assemblées générales")
    public ResponseEntity<AGListResponseDTO> getMeetingsList(
            @RequestParam(required = false) MeetingStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(syndicMeetingService.getMeetingsList(status, search, page, size));
    }

    @GetMapping("/{meetingId}")
    @Operation(summary = "Détail d'une assemblée générale (Vue générale - Onglet 1)")
    public ResponseEntity<MeetingDetailAGDTO> getMeetingDetail(@PathVariable Long meetingId) {
        return ResponseEntity.ok(syndicMeetingService.getMeetingDetail(meetingId));
    }

    @PostMapping("/{meetingId}/publish")
    @Operation(summary = "Publier une assemblée générale (DRAFT -> UPCOMING) (Onglet 1)")
    public ResponseEntity<String> publishMeeting(@PathVariable Long meetingId) {
        syndicMeetingService.publishMeeting(meetingId);
        return ResponseEntity.ok("Réunion publiée avec succès");
    }

    @GetMapping("/{meetingId}/participants")
    @Operation(summary = "Liste des participants d'une assemblée générale (Onglet 2)")
    public ResponseEntity<MeetingParticipantsTabResponseDTO> getMeetingParticipants(
            @PathVariable Long meetingId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(syndicMeetingService.getMeetingParticipants(meetingId,page,size));
    }

    @PostMapping("/{meetingId}/participants/{participantId}/sign")
    @Operation(summary = "Signer la présence d'un participant (Onglet 2)")
    public ResponseEntity<String> signPresence(
            @PathVariable Long meetingId,
            @PathVariable Long participantId,
            @RequestBody SignPresenceDTO dto) {
        syndicMeetingService.signPresence(meetingId, participantId, dto);
        return ResponseEntity.ok("Présence mise à jour avec succès");
    }

    @GetMapping("/{meetingId}/agenda")
    @Operation(summary = "Liste des points de l'ordre du jour (Onglet 3)")
    public ResponseEntity<AgendaItemsTabResponseDTO> getAgendaItems(@PathVariable Long meetingId) {
        return ResponseEntity.ok(syndicMeetingService.getAgendaItems(meetingId));
    }

    @GetMapping("/{meetingId}/resolutions")
    @Operation(summary = "Liste des résolutions d'une assemblée générale (Onglet 4)")
    public ResponseEntity<ResolutionsTabResponseDTO> getResolutions(@PathVariable Long meetingId) {
        return ResponseEntity.ok(syndicMeetingService.getResolutions(meetingId));
    }

    @PutMapping("/{meetingId}/resolutions/{agendaItemId}")
    @Operation(summary = "Mettre à jour une résolution (Onglet 4)")
    public ResponseEntity<String> updateResolution(
            @PathVariable Long meetingId,
            @PathVariable Long agendaItemId,
            @RequestBody UpdateResolutionDTO dto) {
        syndicMeetingService.updateResolution(meetingId, agendaItemId, dto);
        return ResponseEntity.ok("Résolution mise à jour avec succès");
    }

    @GetMapping("/{meetingId}/documents")
    @Operation(summary = "Liste des documents d'une assemblée générale (Onglet 5)")
    public ResponseEntity<MeetingDocumentsTabResponseDTO> getMeetingDocuments(@PathVariable Long meetingId,
                                                                              @RequestParam(defaultValue = "0") Integer page,
                                                                              @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(syndicMeetingService.getMeetingDocuments(meetingId,page,size));
    }

    @PostMapping("/{meetingId}/documents")
    @Operation(summary = "Ajouter des documents à une assemblée générale (Onglet 5)")
    public ResponseEntity<List<MeetingDocumentRowDTO>> addMeetingDocuments(
            @PathVariable Long meetingId,
            @RequestPart("files") List<MultipartFile> files) {
        return ResponseEntity.ok(syndicMeetingService.addMeetingDocuments(meetingId, files));
    }

    @GetMapping("/{meetingId}/history")
    @Operation(summary = "Historique des événements d'une assemblée générale (Onglet 6)")
    public ResponseEntity<MeetingHistoryTabResponseDTO> getMeetingHistory(
            @PathVariable Long meetingId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ResponseEntity.ok(syndicMeetingService.getMeetingHistory(meetingId,page,size));
    }

    @DeleteMapping("/{meetingId}")
    @Operation(summary = "Supprimer une assemblée générale (uniquement en statut DRAFT)")
    public ResponseEntity<String> deleteMeeting(@PathVariable Long meetingId) {
        syndicMeetingService.deleteMeeting(meetingId);
        return ResponseEntity.ok("Réunion supprimée avec succès");
    }

    @Operation(summary = "Liste légère des réunions d'une résidence (pour un sélecteur)")
    @GetMapping("/residences/{residenceId}/summaries")
    public ResponseEntity<List<MeetingSummaryDTO>> getMeetingSummariesByResidence(@PathVariable Long residenceId) {
        return ResponseEntity.ok(syndicMeetingService.getMeetingSummariesByResidence(residenceId));
    }

    @Operation(summary = "Créer un nouveau document AG complet (page Documents générale)")
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MeetingDocumentRowDTO> createMeetingDocument(
            @RequestParam Long meetingId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @Parameter(description = "Date du document au format jj/mm/aaaa", schema = @Schema(type = "string", example = "15/06/2026"))
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate documentDate,
            @RequestParam(required = false) MeetingDocumentType documentType,
            @RequestPart("file") MultipartFile file) {

        CreateMeetingDocumentDTO dto = CreateMeetingDocumentDTO.builder()
                .meetingId(meetingId)
                .title(title)
                .description(description)
                .documentDate(documentDate)
                .documentType(documentType)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(syndicMeetingService.createMeetingDocument(dto, file));
    }

    @Operation(summary = "Mettre à jour les métadonnées d'un document AG existant (page Documents générale)")
    @PatchMapping("/documents/{documentId}")
    public ResponseEntity<MeetingDocumentRowDTO> updateMeetingDocument(
            @PathVariable Long documentId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @Parameter(description = "Date du document au format jj/mm/aaaa", schema = @Schema(type = "string", example = "15/06/2026"))
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate documentDate,
            @RequestParam(required = false) MeetingDocumentType documentType) {

        UpdateMeetingDocumentDTO dto = UpdateMeetingDocumentDTO.builder()
                .title(title)
                .description(description)
                .documentDate(documentDate)
                .documentType(documentType)
                .build();

        return ResponseEntity.ok(syndicMeetingService.updateMeetingDocument(documentId, dto));

    }

    @Operation(summary = "Supprimer un document AG (page Documents générale)")
    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Void> deleteMeetingDocument(@PathVariable Long documentId) {
        syndicMeetingService.deleteMeetingDocument(documentId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Listing général des documents AG (recherche + filtres) (page Documents générale)")
    @GetMapping("/documents")
    public ResponseEntity<MeetingDocumentListResponseDTO> getMeetingDocumentsList(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) MeetingDocumentType documentType,
            @RequestParam(required = false) Long residenceId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "12") Integer size) {

        return ResponseEntity.ok(syndicMeetingService.getMeetingDocumentsList(
                search, documentType, residenceId, page, size));
    }

    // ===== Détail d'un document (quorum, documents liés, résolutions, historique) =====
    @Operation(summary = "Détail d'un document AG (quorum, documents liés, historique)(page Documents générale)")
    @GetMapping("/documents/{documentId}")
    public ResponseEntity<MeetingDocumentDetailDTO> getMeetingDocumentDetail(@PathVariable Long documentId) {
        return ResponseEntity.ok(syndicMeetingService.getMeetingDocumentDetail(documentId));
    }
}

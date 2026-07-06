package com.example.solimus.controllers;

import com.example.solimus.dtos.meeting.AddAgendaItemDTO;
import com.example.solimus.dtos.meeting.AddExternalParticipantDTO;
import com.example.solimus.dtos.meeting.CreateMeetingDTO;
import com.example.solimus.dtos.meeting.InviteParticipantsDTO;
import com.example.solimus.dtos.meeting.MeetingAgendaItemDTO;
import com.example.solimus.dtos.meeting.MeetingCalendarDayDTO;
import com.example.solimus.dtos.meeting.MeetingCardDTO;
import com.example.solimus.dtos.meeting.MeetingDashboardResponseDTO;
import com.example.solimus.dtos.meeting.MeetingDetailDTO;
import com.example.solimus.dtos.meeting.MeetingDetailSyndicDTO;
import com.example.solimus.dtos.meeting.MeetingDocumentDTO;
import com.example.solimus.dtos.meeting.MeetingParticipantsResponseDTO;
import com.example.solimus.dtos.meeting.MeetingSummaryDTO;
import com.example.solimus.dtos.meeting.UpdateMeetingConvocationDTO;
import com.example.solimus.dtos.syndic.residence.ActivityLogItemDTO;
import com.example.solimus.enums.MeetingDocumentType;
import com.example.solimus.services.syndic.syndicAG.SyndicMeetingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/syndic/meetings")
@RequiredArgsConstructor
@Tag(name = "Syndic - Réunions", description = "Gestion des réunions par le syndic")
public class SyndicMeetingController {

    private final SyndicMeetingService meetingService;

    // =========================================================================
    // SYNDIC — CRÉATION ET GESTION
    // =========================================================================

    @Operation(summary = "Dashboard des AG — KPIs + liste filtrable", tags = {"Syndic - Assemblées Générales"})
    @GetMapping
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<MeetingDashboardResponseDTO> getMeetingsDashboard(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "12") Integer size) {
        return ResponseEntity.ok(meetingService.getMeetingsDashboard(search, status, page, size));
    }

    @Operation(summary = "Créer une AG — Étape 1 (Informations générales)")
    @PostMapping
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<MeetingSummaryDTO> createMeeting(
            @Valid @RequestBody CreateMeetingDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(meetingService.createMeeting(dto));
    }

    @Operation(summary = "Créer une AG — Étape 2 (Convocations)", tags = {"Syndic - Assemblées Générales"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PatchMapping("/meetings/{meetingId}/convocation")
    public ResponseEntity<MeetingSummaryDTO> updateConvocation(
            @PathVariable Long meetingId,
            @RequestBody @Valid UpdateMeetingConvocationDTO dto) {
        return ResponseEntity.ok(meetingService.updateConvocation(meetingId, dto));
    }

    @Operation(summary = "Publier une AG — envoie les convocations et fige les participants", tags = {"Syndic - Assemblées Générales"})
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    @PostMapping("/meetings/{meetingId}/publish")
    public ResponseEntity<MeetingSummaryDTO> publishMeeting(@PathVariable Long meetingId) {
        return ResponseEntity.ok(meetingService.publishMeeting(meetingId));
    }

    @Operation(summary = "Ajouter un point à l'ordre du jour")
    @PostMapping("/{meetingId}/agenda")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<MeetingAgendaItemDTO> addAgendaItem(
            @PathVariable Long meetingId,
            @Valid @RequestBody AddAgendaItemDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(meetingService.addAgendaItem(meetingId, dto));
    }

    @Operation(summary = "Uploader un document joint (PDF, etc.)")
    @PostMapping(value = "/{meetingId}/documents",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<MeetingDocumentDTO> uploadDocument(
            @PathVariable Long meetingId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileName") String fileName
           ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(meetingService.uploadDocument(meetingId, file, fileName));
    }

    @Operation(summary = "Inviter des copropriétaires à une réunion")
    @PostMapping("/{meetingId}/participants")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<String> inviteParticipants(
            @PathVariable Long meetingId,
            @Valid @RequestBody InviteParticipantsDTO dto) {
        meetingService.inviteParticipants(meetingId, dto);
        return ResponseEntity.ok("Participants invités avec succès.");
    }

    @Operation(summary = "Ajouter un participant externe (nom + rôle libre)")
    @PostMapping("/{meetingId}/participants/external")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<String> addExternalParticipant(
            @PathVariable Long meetingId,
            @Valid @RequestBody AddExternalParticipantDTO dto) {
        meetingService.addExternalParticipant(meetingId, dto);
        return ResponseEntity.ok("Participant externe ajouté avec succès.");
    }

    // =========================================================================
    // LECTURE — LISTE + DÉTAIL + CALENDRIER
    // =========================================================================

    @Operation(summary = "Liste des réunions d'une résidence")
    @GetMapping("/residence/{residenceId}")
    @PreAuthorize("hasAnyRole('ROLE_SYNDIC', 'ROLE_COPROPRIETAIRE')")
    public ResponseEntity<List<MeetingSummaryDTO>> getMeetingsByResidence(
            @PathVariable Long residenceId) {
        return ResponseEntity.ok(
                meetingService.getMeetingsByResidence(residenceId));
    }

    @Operation(summary = "Détail complet d'une réunion")
    @GetMapping("/{meetingId}")
    @PreAuthorize("hasAnyRole('ROLE_SYNDIC', 'ROLE_COPROPRIETAIRE')")
    public ResponseEntity<MeetingDetailDTO> getMeetingDetail(
            @PathVariable Long meetingId) {
        return ResponseEntity.ok(meetingService.getMeetingDetail(meetingId));
    }

    @Operation(summary = "Détail d'une AG pour le syndic — KPIs + Vue Générale", tags = {"Syndic - Assemblées Générales"})
    @GetMapping("/{meetingId}/syndic")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<MeetingDetailSyndicDTO> getMeetingDetailSyndic(
            @PathVariable Long meetingId) {
        return ResponseEntity.ok(meetingService.getMeetingDetailSyndic(meetingId));
    }

    @Operation(summary = "Liste des participants d'une AG avec filtre optionnel sur le statut", tags = {"Syndic - Assemblées Générales"})
    @GetMapping("/{meetingId}/participants")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<MeetingParticipantsResponseDTO> getMeetingParticipants(
            @PathVariable Long meetingId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(meetingService.getMeetingParticipants(meetingId, status));
    }

    @Operation(summary = "Liste des points de l'ordre du jour d'une AG", tags = {"Syndic - Assemblées Générales"})
    @GetMapping("/{meetingId}/agenda-items")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<List<MeetingAgendaItemDTO>> getAgendaItems(
            @PathVariable Long meetingId) {
        return ResponseEntity.ok(meetingService.getAgendaItems(meetingId));
    }

    @Operation(summary = "Liste des documents d'une AG", tags = {"Syndic - Assemblées Générales"})
    @GetMapping("/{meetingId}/documents")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<List<MeetingDocumentDTO>> getDocuments(
            @PathVariable Long meetingId) {
        return ResponseEntity.ok(meetingService.getDocuments(meetingId));
    }

    @Operation(summary = "Ajouter un document à une AG", tags = {"Syndic - Assemblées Générales"})
    @PostMapping(value = "/{meetingId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<MeetingDocumentDTO> addDocument(
            @PathVariable Long meetingId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) MeetingDocumentType documentType) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(meetingService.addDocument(meetingId, file, documentType));
    }

    @Operation(summary = "Historique d'une AG (via ActivityLog)", tags = {"Syndic - Assemblées Générales"})
    @GetMapping("/{meetingId}/history")
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<List<ActivityLogItemDTO>> getMeetingHistory(
            @PathVariable Long meetingId) {
        return ResponseEntity.ok(meetingService.getMeetingHistory(meetingId));
    }

    @Operation(summary = "Vue calendrier — réunions groupées par jour pour un mois donné")
    @GetMapping("/calendar/{residenceId}")
    @PreAuthorize("hasAnyRole('ROLE_SYNDIC', 'ROLE_COPROPRIETAIRE')")
    public ResponseEntity<List<MeetingCalendarDayDTO>> getMeetingsCalendar(
            @PathVariable Long residenceId,
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(
                meetingService.getMeetingsCalendar(residenceId, year, month));
    }
}

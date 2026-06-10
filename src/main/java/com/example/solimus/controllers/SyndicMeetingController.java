package com.example.solimus.controllers;

import com.example.solimus.dtos.meeting.AddAgendaItemDTO;
import com.example.solimus.dtos.meeting.AddExternalParticipantDTO;
import com.example.solimus.dtos.meeting.CreateMeetingDTO;
import com.example.solimus.dtos.meeting.InviteParticipantsDTO;
import com.example.solimus.dtos.meeting.MeetingAgendaItemDTO;
import com.example.solimus.dtos.meeting.MeetingCalendarDayDTO;
import com.example.solimus.dtos.meeting.MeetingDetailDTO;
import com.example.solimus.dtos.meeting.MeetingDocumentDTO;
import com.example.solimus.dtos.meeting.MeetingSummaryDTO;
import com.example.solimus.services.syndic.SyndicMeetingService;
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
@Tag(name = "4.h Syndic - Réunions", description = "Gestion des réunions par le syndic")
public class SyndicMeetingController {

    private final SyndicMeetingService meetingService;

    // =========================================================================
    // SYNDIC — CRÉATION ET GESTION
    // =========================================================================

    @Operation(summary = "Créer une réunion")
    @PostMapping
    @PreAuthorize("hasRole('ROLE_SYNDIC')")
    public ResponseEntity<String> createMeeting(
            @Valid @RequestBody CreateMeetingDTO dto) {
        meetingService.createMeeting(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Réunion créée avec succès.");
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
            @RequestParam("fileName") String fileName,
            @RequestParam("documentType") String documentType) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(meetingService.uploadDocument(meetingId, file,
                        fileName, documentType));
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

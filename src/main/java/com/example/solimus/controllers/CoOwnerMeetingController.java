package com.example.solimus.controllers;

import com.example.solimus.dtos.meeting.MeetingCalendarDayDTO;
import com.example.solimus.dtos.meeting.MeetingDetailDTO;
import com.example.solimus.dtos.meeting.MeetingSummaryDTO;
import com.example.solimus.services.coproprietaire.CoOwnerMeetingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coOwner/meetings")
@RequiredArgsConstructor
@Tag(name = "CoOwner - Réunions", description = "Consultation des réunions du copropriétaire")
public class CoOwnerMeetingController {

    private final CoOwnerMeetingService meetingService;

    @Operation(summary = "Lister mes réunions")
    @GetMapping
    public ResponseEntity<List<MeetingSummaryDTO>> getMyMeetings() {
        return ResponseEntity.ok(meetingService.getMyMeetings());
    }

    @Operation(summary = "Nombre de réunions à venir")
    @GetMapping("/upcoming/count")
    public ResponseEntity<Long> getUpcomingMeetingsCount() {
        return ResponseEntity.ok(meetingService.getUpcomingMeetingsCount());
    }

    @Operation(summary = "Détail d'une réunion")
    @GetMapping("/{meetingId}")
    public ResponseEntity<MeetingDetailDTO> getMeetingDetail(
            @Parameter(description = "ID de la réunion")
            @PathVariable Long meetingId) {
        return ResponseEntity.ok(meetingService.getMeetingDetail(meetingId));
    }

    // =========================================================================
    // LECTURE — COPROPRIÉTAIRE + SYNDIC
    // =========================================================================

    @Operation(summary = "Liste des réunions d'une résidence")
    @GetMapping("/residence/{residenceId}")
    @PreAuthorize("hasAnyRole('ROLE_SYNDIC', 'ROLE_COPROPRIETAIRE')")
    public ResponseEntity<List<MeetingSummaryDTO>> getMeetingsByResidence(
            @PathVariable Long residenceId) {
        return ResponseEntity.ok(
                meetingService.getMeetingsByResidence(residenceId));
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

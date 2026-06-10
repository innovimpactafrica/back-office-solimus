package com.example.solimus.controllers;

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
@RequestMapping("/api/coowner/meetings")
@RequiredArgsConstructor
@Tag(name = "3.d Copropriétaire - Réunions", description = "Consultation des réunions et assemblées générales.")
public class OwnerMeetingController {

    private final CoOwnerMeetingService meetingService;

    @Operation(summary = "Lister mes réunions")
    @GetMapping
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<List<MeetingSummaryDTO>> getMyMeetings() {
        return ResponseEntity.ok(meetingService.getMyMeetings());
    }

    @Operation(summary = "Nombre de réunions à venir")
    @GetMapping("/upcoming/count")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<Long> getUpcomingMeetingsCount() {
        return ResponseEntity.ok(meetingService.getUpcomingMeetingsCount());
    }

    @Operation(summary = "Détail d'une réunion")
    @GetMapping("/{meetingId}")
    @PreAuthorize("hasRole('ROLE_COPROPRIETAIRE')")
    public ResponseEntity<MeetingDetailDTO> getMeetingDetail(
            @Parameter(description = "ID de la réunion")
            @PathVariable Long meetingId) {
        return ResponseEntity.ok(meetingService.getMeetingDetail(meetingId));
    }
}

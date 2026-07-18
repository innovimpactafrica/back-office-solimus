package com.example.solimus.controllers;

import com.example.solimus.dtos.owner.meeting.*;
import com.example.solimus.services.owner.meeting.OwnerMeetingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/owner/meetings")
@RequiredArgsConstructor
@Tag(name = "Copropriétaire - Réunions", description = "Gestion des assemblées générales pour les copropriétaires")
public class OwnerMeetingController {

    private final OwnerMeetingService ownerMeetingService;

    @GetMapping
    @Operation(summary = "Liste des réunions à venir du copropriétaire (onglet Réunion, vue Liste)")
    public ResponseEntity<OwnerMeetingListResponseDTO> getOwnerMeetings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ownerMeetingService.getOwnerMeetings(page, size));
    }

    @GetMapping("/{meetingId}")
    @Operation(summary = "Détail d'une réunion (ordre du jour, documents, organisateur...)")
    public ResponseEntity<OwnerMeetingDetailDTO> getOwnerMeetingDetail(
            @PathVariable Long meetingId,
            @RequestParam(defaultValue = "0") int documentPage,
            @RequestParam(defaultValue = "10") int documentSize) {
        return ResponseEntity.ok(ownerMeetingService.getOwnerMeetingDetail(meetingId, documentPage, documentSize));
    }

    @GetMapping("/calendar/month")
    @Operation(summary = "Réunions à venir d'un mois précis, groupées par jour (vue Calendrier)")
    public ResponseEntity<OwnerMeetingCalendarDTO> getOwnerMeetingsCalendar(
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(ownerMeetingService.getOwnerMeetingsCalendar(year, month));
    }
}

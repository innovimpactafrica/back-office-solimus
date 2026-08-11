package com.example.solimus.controllers;

import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.owner.meeting.*;
import com.example.solimus.services.owner.meeting.OwnerMeetingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/owner/meetings")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_COPROPRIETAIRE')")
@Tag(name = "Copropriétaire - Réunions", description = "Gestion des assemblées générales pour les copropriétaires")
public class OwnerMeetingController {

    private final OwnerMeetingService ownerMeetingService;

    @GetMapping
    @Operation(summary = "Liste des réunions à venir du copropriétaire (onglet Réunion, vue Liste)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès",
                    content = @Content(schema = @Schema(implementation = OwnerMeetingListResponseDTO.class)))
    })
    public ResponseEntity<OwnerMeetingListResponseDTO> getOwnerMeetings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ownerMeetingService.getOwnerMeetings(page, size));
    }

    @GetMapping("/{meetingId}")
    @Operation(summary = "Détail d'une réunion (ordre du jour, documents, organisateur...)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = OwnerMeetingDetailDTO.class))),
            @ApiResponse(responseCode = "403", description = "Vous n'êtes pas convoqué à cette réunion",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Réunion introuvable",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<OwnerMeetingDetailDTO> getOwnerMeetingDetail(
            @PathVariable Long meetingId,
            @RequestParam(defaultValue = "0") int documentPage,
            @RequestParam(defaultValue = "10") int documentSize) {
        return ResponseEntity.ok(ownerMeetingService.getOwnerMeetingDetail(meetingId, documentPage, documentSize));
    }

    @GetMapping("/calendar/month")
    @Operation(summary = "Réunions à venir d'un mois précis, groupées par jour (vue Calendrier)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Calendrier renvoyé avec succès",
                    content = @Content(schema = @Schema(implementation = OwnerMeetingCalendarDTO.class)))
    })
    public ResponseEntity<OwnerMeetingCalendarDTO> getOwnerMeetingsCalendar(
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(ownerMeetingService.getOwnerMeetingsCalendar(year, month));
    }
}

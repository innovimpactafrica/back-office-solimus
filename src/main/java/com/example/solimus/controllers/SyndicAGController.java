package com.example.solimus.controllers;

import com.example.solimus.dtos.meeting.MeetingDetailDTO;
import com.example.solimus.dtos.syndic.meeting.CreateMeetingDTO;
import com.example.solimus.services.syndic.syndicAG.SyndicMeetingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/syndic/ag")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_SYNDIC')")
public class SyndicAGController {

    private final SyndicMeetingService syndicMeetingService;

    @PostMapping("/create")
    public ResponseEntity<MeetingDetailDTO> createMeeting(
            @RequestPart("dto") CreateMeetingDTO dto,
            @RequestPart(value = "documents", required = false) List<MultipartFile> documents) {
        MeetingDetailDTO response = syndicMeetingService.createMeeting(dto, documents);
        return ResponseEntity.ok(response);
    }
}

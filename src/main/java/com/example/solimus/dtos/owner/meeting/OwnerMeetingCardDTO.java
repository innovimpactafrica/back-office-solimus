package com.example.solimus.dtos.owner.meeting;

import com.example.solimus.enums.MeetingStatus;
import com.example.solimus.enums.MeetingType;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;

// ===== DTO CARTE REUNION - APP MOBILE COPROPRIETAIRE =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerMeetingCardDTO {

    private Long id;
    private String title;

    private MeetingType type;
    private String typeLabel;

    private MeetingStatus status;
    private String statusLabel;

    private LocalDate meetingDate;
    private LocalTime startTime;
    private LocalTime endTime; // peut être null si non renseignée

    private String location;

    private long participantsCount;
    private long documentsCount;
}
package com.example.solimus.dtos.syndic.meeting;

import lombok.*;
import java.time.LocalDate;

// ===== DTO RÉSUMÉ - POUR LE SÉLECTEUR DE RÉUNION =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingSummaryDTO {

    private Long id;
    private String title;
    private LocalDate meetingDate;
}
package com.example.solimus.repositories.meeting;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

// ===== PROJECTION STATS DE PARTICIPATION PAR REUNION =====
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class MeetingParticipationStats {

    private Long meetingId;
    private long totalParticipants;
    private long signedCount;
    private BigDecimal totalTantieme;   
    private BigDecimal signedTantieme;  }

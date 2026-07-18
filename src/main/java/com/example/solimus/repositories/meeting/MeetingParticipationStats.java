package com.example.solimus.repositories.meeting;

import java.math.BigDecimal;

// ===== PROJECTION STATS DE PARTICIPATION PAR REUNION =====
public class MeetingParticipationStats {

    private Long meetingId;
    private Long totalParticipants;
    private Long signedCount;
    private BigDecimal totalTantieme;
    private BigDecimal signedTantieme;

    public MeetingParticipationStats(Long meetingId, Long totalParticipants, Long signedCount,
                                     BigDecimal totalTantieme, BigDecimal signedTantieme) {
        this.meetingId = meetingId;
        this.totalParticipants = totalParticipants;
        this.signedCount = signedCount;
        this.totalTantieme = totalTantieme;
        this.signedTantieme = signedTantieme;
    }

    public MeetingParticipationStats() {
    }

    public Long getMeetingId() {
        return meetingId;
    }

    public Long getTotalParticipants() {
        return totalParticipants;
    }

    public Long getSignedCount() {
        return signedCount;
    }

    public BigDecimal getTotalTantieme() {
        return totalTantieme;
    }

    public BigDecimal getSignedTantieme() {
        return signedTantieme;
    }
}

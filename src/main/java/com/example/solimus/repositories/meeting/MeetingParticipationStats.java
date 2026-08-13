package com.example.solimus.repositories.meeting;

import java.math.BigDecimal;

// ===== PROJECTION STATS DE PARTICIPATION PAR REUNION =====
public class MeetingParticipationStats {

    private Long meetingId;
    private Long totalParticipants;
    private Long presentCount;         // uniquement AttendanceType.PRESENT
    private Long procurationsCount;    // uniquement AttendanceType.PROXY
    private BigDecimal totalTantieme;
    private BigDecimal participatingTantieme; // tantième des PRESENT + PROXY (compte pour le quorum)

    public MeetingParticipationStats(Long meetingId, Long totalParticipants, Long presentCount,
                                      Long procurationsCount, BigDecimal totalTantieme,
                                      BigDecimal participatingTantieme) {
        this.meetingId = meetingId;
        this.totalParticipants = totalParticipants;
        this.presentCount = presentCount;
        this.procurationsCount = procurationsCount;
        this.totalTantieme = totalTantieme;
        this.participatingTantieme = participatingTantieme;
    }

    public MeetingParticipationStats() {
    }

    public Long getMeetingId() {
        return meetingId;
    }

    public Long getTotalParticipants() {
        return totalParticipants;
    }

    public Long getPresentCount() {
        return presentCount;
    }

    public Long getProcurationsCount() {
        return procurationsCount;
    }

    public BigDecimal getTotalTantieme() {
        return totalTantieme;
    }

    public BigDecimal getParticipatingTantieme() {
        return participatingTantieme;
    }
}

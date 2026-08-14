package com.example.solimus.repositories.meeting;

// ===== PROJECTION STATS DE PARTICIPATION PAR REUNION (calcul par tête, pas par tantième) =====
public class MeetingParticipationStats {

    private Long meetingId;
    private Long totalParticipants;
    private Long presentCount;         // uniquement AttendanceType.PRESENT
    private Long procurationsCount;    // uniquement AttendanceType.PROXY

    public MeetingParticipationStats(Long meetingId, Long totalParticipants, Long presentCount,
                                      Long procurationsCount) {
        this.meetingId = meetingId;
        this.totalParticipants = totalParticipants;
        this.presentCount = presentCount;
        this.procurationsCount = procurationsCount;
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
}

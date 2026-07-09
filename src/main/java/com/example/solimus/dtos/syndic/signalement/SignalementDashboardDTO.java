package com.example.solimus.dtos.syndic.signalement;

import lombok.Builder;
import lombok.Data;

//DTO des 4 cards du dashboard "Gestion des signalements"
@Data
@Builder
public class SignalementDashboardDTO {
    private long total;
    private long inProgress;
    private long resolved;
    private long pending;
}

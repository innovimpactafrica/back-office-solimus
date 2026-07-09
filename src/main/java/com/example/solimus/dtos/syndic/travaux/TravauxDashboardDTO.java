package com.example.solimus.dtos.syndic.travaux;

import lombok.Builder;
import lombok.Data;

//DTO des 6 KPIs du dashboard "Gestion des demandes travaux"
@Data
@Builder
public class TravauxDashboardDTO {
    private long ouverts; // hors FINAL_VALIDATION / CANCELLED
    private long urgents; // parmi les ouverts, urgencyLevel = HIGH
    private long enAttenteDevis; // status = PENDING
    private long enCours; // status = STARTED
    private long resolus; // status = FINISHED
    private long clotures; // status = FINAL_VALIDATION
}

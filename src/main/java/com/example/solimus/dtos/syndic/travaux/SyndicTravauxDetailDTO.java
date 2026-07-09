package com.example.solimus.dtos.syndic.travaux;

import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.UrgencyLevel;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

//DTO de la Vue Générale d'un incident travaux (onglet 1, syndic)
@Data
@Builder
public class SyndicTravauxDetailDTO {
    private Long id;
    private String reference;
    private String title;
    private String description;
    private UrgencyLevel urgencyLevel;
    private InterventionStatus status;
    private String statusLabel;
    private String residenceName;
    private String positionLabel;
    private String specialtyName;
    private String declaredByName;
    private LocalDateTime createdAt;

    // Résumé (bandeau haut)
    private String prestataireName; // null si aucun devis accepté
    private BigDecimal coutEstime; // totalAmount de l'intervention
    private String dureeEstimee; // estimatedDelay du devis accepté, si présent

    // Résumé financier
    private BigDecimal devisInitial; // totalAmount
    private BigDecimal avanceVersee; // depositAmount (acompte)
    private BigDecimal totalEngage; // totalAmount (identique au devis initial)
    private BigDecimal totalPaye; // depositAmount, ou totalAmount si tout est payé

    // Participants (règle : SYNDIC seul si initiatedBy=SYNDIC sans owner,
    // OWNER+SYNDIC si initiatedBy=OWNER, +PRESTATAIRE si selectedProvider existe)
    private List<ParticipantDTO> participants;

    private List<String> photoUrls;
}

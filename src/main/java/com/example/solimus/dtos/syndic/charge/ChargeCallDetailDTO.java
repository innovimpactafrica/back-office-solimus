package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

//DTO du détail d'un appel de charges (KPIs + suivi par copropriétaire)
@Data
public class ChargeCallDetailDTO {
    private Long id; // Identifiant de l'appel
    private String reference; // Ex: CC-2026-T1-B12
    private String residenceName; // Nom de la résidence
    private String periodLabel; // Ex: "T1 2026 (Jan-Mar)"
    private BigDecimal totalAmount; // Montant total de l'appel
    private BigDecimal totalCollected; // Total encaissé = SUM(items.paidAmount)
    private BigDecimal remainingBalance; // Solde restant = totalAmount - totalCollected
    private String status; // SOLDE, PARTIEL, ENVOYE
    private String budgetReference; // Ex: BUD-2026-001
    private Integer collectedPercentage; // Pourcentage recouvré
    private LocalDate sentDate; // Date d'envoi
    private LocalDate dueDate; // Date d'échéance
    private List<ChargeCallItemDetailDTO> items; // Suivi par copropriétaire
}

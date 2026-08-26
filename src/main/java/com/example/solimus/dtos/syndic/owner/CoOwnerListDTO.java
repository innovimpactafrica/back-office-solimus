package com.example.solimus.dtos.syndic.owner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

//DTO pour la liste des copropriétaires du syndic connecté
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoOwnerListDTO {

    private Long id;

    private String fullName;

    private String photoUrl;

    private String email;

    private String phone;

    // Nombre d'appartements (lots) du copropriétaire, restreint aux résidences du syndic
    private int apartmentsCount;

    // Nombre de résidences distinctes où le copropriétaire a des lots, restreint au syndic
    private int residencesCount;

    // Solde global : SUM(paidAmount) - SUM(totalDue) pour tous les ChargeCallItems
    // Négatif = doit de l'argent, Zéro ou positif = à jour — seul indicateur de situation
    // financière sur cette liste (le statut A_JOUR/RETARD/IMPAYE reste disponible sur le détail)
    private BigDecimal solde;
}

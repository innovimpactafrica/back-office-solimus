package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;

//DTO d'une ligne du tableau "Impayés" (global syndic)
@Data
public class UnpaidRowDTO {
    private Long chargeCallItemId;
    private String coOwnerName;
    private String propertyLabel;
    private String residenceName;
    private String period; // ex. "T3" (trimestriel) ou "Jan" (mensuel)
    private Integer year;
    private String status; // IMPAYE, RETARD
    private BigDecimal amountDue;
    // unpaidBalance supprimé : sans paiement partiel, un item PENDING a toujours un solde
    // strictement égal à amountDue — colonne redondante, n'apportait plus d'info distincte
    private Integer daysLate;
}

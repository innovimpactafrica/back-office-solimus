package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

//DTO d'une ligne du tableau "Paiements reçus" (onglet Paiements)
@Data
public class ExceptionalCallPaymentDTO {
    private String coOwnerName; // Nom du copropriétaire qui a payé
    private LocalDateTime paidAt; // Date du paiement
    private BigDecimal amount; // Montant payé
    private String method; // Moyen de paiement utilisé
    private String reference; // Référence de la transaction
}

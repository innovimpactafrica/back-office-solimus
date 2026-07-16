package com.example.solimus.dtos.syndic.finance;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

//DTO d'une ligne du tableau "Paiements" du module Finances (paiements de charges courantes par les copropriétaires)
@Data
public class FinancePaymentRowDTO {
    private LocalDate date;
    private String coOwnerName;
    private String residenceName;
    private String type; // "Charges T" + periodNumber, même format pour mensuel et trimestriel
    private BigDecimal amount;
    private String status; // "Validé" si le paiement est COMPLETED
}

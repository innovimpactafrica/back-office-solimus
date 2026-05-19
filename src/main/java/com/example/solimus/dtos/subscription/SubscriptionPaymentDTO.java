package com.example.solimus.dtos.subscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPaymentDTO {
    private String reference;
    private String plan;
    private BigDecimal montant;

    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate date;
    private String moyenPaiement;
    private String statut;
}

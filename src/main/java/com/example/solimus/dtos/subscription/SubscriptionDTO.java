package com.example.solimus.dtos.subscription;

import com.example.solimus.enums.SubscriptionStatus;
import com.example.solimus.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionDTO {
    private String plan;
    private SubscriptionStatus status;
    private boolean active;

    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate dateActivation;

    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate dateExpiration;
    private PaymentMethod moyenPaiement;
    private boolean renouvellementAuto;
    private List<String> avantages;
    private List<SubscriptionPaymentDTO> historiquePaiements;
}

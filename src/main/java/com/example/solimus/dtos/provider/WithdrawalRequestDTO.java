package com.example.solimus.dtos.provider;

import com.example.solimus.enums.PaymentMethod;
import com.example.solimus.enums.WithdrawalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO retourné après la création ou lors de la consultation d'une demande de versement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalRequestDTO {
    private Long id;
    private String reference;
    private BigDecimal montant;
    private PaymentMethod methode;
    private String numeroDeTelephone;
    private WithdrawalStatus statut;
    private LocalDateTime createdAt;
}

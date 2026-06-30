package com.example.solimus.dtos.provider.wallet;

import com.example.solimus.enums.PaymentMethod;
import com.example.solimus.enums.WithdrawalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO retourné après la création d'une demande de versement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalRequestDTO {
    private Long id;
    private String reference;
    private BigDecimal amount;
    private PaymentMethod method;
    private String phoneNumber;
    private WithdrawalStatus status;
    private LocalDateTime createdAt;
}

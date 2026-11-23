package com.example.solimus.dtos.provider.wallet.recharge;

import com.example.solimus.enums.OffPlatformPaymentMethod;
import com.example.solimus.enums.ProofStatus;
import com.example.solimus.enums.RechargeOrigin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderWalletRechargeDTO {
    private Long id;
    private String reference;
    private Long providerId;
    private String providerName;
    private String providerEmail;
    private String providerPhone;
    private BigDecimal amount;
    private OffPlatformPaymentMethod paymentMethod;
    private String paymentMethodLabel;
    private LocalDate paymentDate;
    private String description;
    /** Lien temporaire vers le justificatif (null pour un crédit direct de l'admin). */
    private String documentUrl;
    private String documentName;
    private String documentContentType;
    private RechargeOrigin origin;
    private String originLabel;
    private ProofStatus status;
    private String statusLabel;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private String reviewedByName;
    private String rejectionReason;
}

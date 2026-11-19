package com.example.solimus.dtos.payment.proof;

import com.example.solimus.enums.OffPlatformPaymentMethod;
import com.example.solimus.enums.PaymentType;
import com.example.solimus.enums.ProofPayerType;
import com.example.solimus.enums.ProofStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Preuve de paiement hors plateforme d'un acompte ou d'un solde de travaux")
public class InterventionPaymentProofDTO {

    private Long id;
    @Schema(example = "PTV-000017")
    private String reference;

    private Long interventionId;
    private String interventionReference;
    private String interventionTitle;
    private String residenceName;

    @Schema(description = "ACOMPTE ou SOLDE")
    private PaymentType type;
    private String typeLabel;
    private BigDecimal amount;
    @Schema(description = "Montant TTC du devis accepté, pour contexte")
    private BigDecimal quoteTotalAmount;

    private ProofPayerType payerType;
    private String payerName;
    private Long providerId;
    private String providerName;

    private OffPlatformPaymentMethod paymentMethod;
    private String paymentMethodLabel;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate paymentDate;
    private String description;

    private String documentUrl;
    private String documentName;
    private String documentContentType;

    private ProofStatus status;
    private String statusLabel;
    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime submittedAt;
    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime reviewedAt;
    private String rejectionReason;
    @Schema(description = "Référence du paiement enregistré à la validation", example = "PAY-000031")
    private String paymentReference;
}

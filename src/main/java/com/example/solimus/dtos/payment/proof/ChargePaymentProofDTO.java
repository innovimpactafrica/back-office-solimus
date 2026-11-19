package com.example.solimus.dtos.payment.proof;

import com.example.solimus.enums.ChargeType;
import com.example.solimus.enums.OffPlatformPaymentMethod;
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
@Schema(description = "Preuve de paiement hors plateforme d'une charge (courante ou exceptionnelle)")
public class ChargePaymentProofDTO {

    private Long id;
    @Schema(example = "PRV-000042")
    private String reference;

    private ChargeType chargeType;
    @Schema(description = "Id de la ligne de charge (ChargeCallItem si REGULAR, ExceptionalCallItem si EXCEPTIONAL)")
    private Long chargeId;
    @Schema(example = "Charges T3 2026 (Jul-Sep)")
    private String chargeTitle;
    private String chargeReference;

    private Long residenceId;
    private String residenceName;
    private Long coOwnerId;
    private String coOwnerName;
    private String propertyReference;

    @Schema(description = "Montant soldé par ce paiement (reste dû au dépôt)", example = "45000")
    private BigDecimal amount;
    private OffPlatformPaymentMethod paymentMethod;
    private String paymentMethodLabel;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate paymentDate;
    private String description;

    @Schema(description = "URL du justificatif (PDF / JPG / PNG)")
    private String documentUrl;
    private String documentName;
    private String documentContentType;

    private ProofStatus status;
    @Schema(example = "En attente de validation")
    private String statusLabel;
    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime submittedAt;
    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime reviewedAt;
    private String reviewedByName;
    private String rejectionReason;
    @Schema(description = "Référence du paiement enregistré à la validation (reçu)", example = "CPY-000120")
    private String paymentReference;
}

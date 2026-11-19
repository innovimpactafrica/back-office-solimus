package com.example.solimus.dtos.payment.proof;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyndicChargeProofPageDTO {

    @Schema(description = "Nombre total de preuves en attente de validation (toutes résidences, sans filtre)")
    private long pendingValidationCount;

    private Page<ChargePaymentProofDTO> proofs;
}

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
public class ProviderPaymentProofPageDTO {

    @Schema(description = "Nombre de paiements déclarés en attente de votre confirmation")
    private long pendingValidationCount;

    private Page<InterventionPaymentProofDTO> proofs;
}

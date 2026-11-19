package com.example.solimus.dtos.payment.proof;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Résultat d'un traitement groupé : chaque preuve est traitée indépendamment")
public class ProofBatchResultDTO {

    private int requested;
    private int succeeded;
    private int failed;
    @Schema(description = "Ids des preuves traitées avec succès")
    private List<Long> succeededIds;
    @Schema(description = "Preuves non traitées, avec la raison")
    private List<Failure> failures;
    private String message;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Failure {
        private Long proofId;
        private String reason;
    }
}

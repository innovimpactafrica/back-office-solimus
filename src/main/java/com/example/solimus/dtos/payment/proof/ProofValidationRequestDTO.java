package com.example.solimus.dtos.payment.proof;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Validation d'une ou plusieurs preuves de paiement")
public class ProofValidationRequestDTO {

    @NotEmpty(message = "Au moins une preuve à valider")
    @Size(max = 100, message = "100 preuves au maximum par validation")
    @Schema(example = "[12, 13, 15]")
    private List<@NotNull Long> proofIds;
}

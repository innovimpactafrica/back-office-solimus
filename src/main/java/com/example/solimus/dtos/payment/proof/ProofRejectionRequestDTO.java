package com.example.solimus.dtos.payment.proof;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Rejet d'une ou plusieurs preuves de paiement, avec le motif communiqué au payeur")
public class ProofRejectionRequestDTO {

    @NotEmpty(message = "Au moins une preuve à rejeter")
    @Size(max = 100, message = "100 preuves au maximum par rejet")
    @Schema(example = "[14]")
    private List<@NotNull Long> proofIds;

    @NotBlank(message = "Le motif du rejet est obligatoire")
    @Size(max = 500, message = "Motif : 500 caractères maximum")
    @Schema(example = "Le montant du virement ne correspond pas à la charge")
    private String reason;
}

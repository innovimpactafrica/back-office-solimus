package com.example.solimus.dtos.provider.wallet.recharge;

import com.example.solimus.enums.OffPlatformPaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Crédit direct du wallet d'un prestataire par l'admin (paiement reçu hors application)")
public class AdminDirectRechargeDTO {

    @NotNull(message = "Le prestataire est obligatoire")
    @Schema(example = "9")
    private Long providerId;

    @NotNull(message = "Le montant est obligatoire")
    @DecimalMin(value = "1", message = "Le montant doit être positif")
    @Schema(example = "5000")
    private BigDecimal amount;

    @Schema(description = "VIREMENT, ESPECES, CHEQUE, WAVE, ORANGE_MONEY ou AUTRE (défaut AUTRE)", example = "ESPECES")
    private OffPlatformPaymentMethod paymentMethod;

    @NotBlank(message = "La description est obligatoire")
    @Size(min = 3, max = 1000, message = "Description : 3 à 1000 caractères")
    @Schema(example = "Versement en espèces au bureau Solimus")
    private String description;
}

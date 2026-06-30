package com.example.solimus.dtos.provider.wallet;

import com.example.solimus.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO envoyé par le prestataire mobile pour demander un versement (retrait).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestWithdrawalDTO {

    @NotNull(message = "Le montant est obligatoire")
    private BigDecimal amount;

    @NotNull(message = "La méthode de versement est obligatoire")
    private PaymentMethod method;       // WAVE ou ORANGE_MONEY

    @NotNull(message = "Le numéro de téléphone de réception est obligatoire")
    private String phoneNumber;
}

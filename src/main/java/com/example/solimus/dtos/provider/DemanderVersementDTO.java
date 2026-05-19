package com.example.solimus.dtos.provider;

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
public class DemanderVersementDTO {
    @NotNull(message = "Le montant est obligatoire")
    private BigDecimal montant;

    @NotNull(message = "La méthode de versement est obligatoire")
    private PaymentMethod methode;       // WAVE ou ORANGE_MONEY

    @NotNull(message = "Le numéro de téléphone de réception est obligatoire")
    private String numeroDeTelephone;    // "+221 77 123 45 67"
}

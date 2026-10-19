package com.example.solimus.dtos.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// Corps d'un paiement Wave / Orange Money pour une référence déjà initiée (SYR-, CPY-, ...)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileMoneyPaymentRequestDTO {

    // Montant de contrôle (facultatif) : le montant facturé est TOUJOURS celui calculé par le serveur
    // (prix de la formule, quote-part, acompte...). S'il est fourni et différent → 400.
    @Positive(message = "Le montant doit être positif")
    @Schema(description = "Montant attendu (contrôle) — le serveur facture toujours son propre montant", example = "15000")
    private BigDecimal amount;

    // Numéro du compte Wave / Orange Money à débiter. Par défaut : le téléphone du profil du payeur.
    @Schema(description = "Numéro Wave / Orange Money (9 chiffres) — défaut : téléphone du profil", example = "771234567")
    private String phoneNumber;
}

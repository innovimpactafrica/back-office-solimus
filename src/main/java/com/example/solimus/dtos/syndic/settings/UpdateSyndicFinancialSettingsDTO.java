package com.example.solimus.dtos.syndic.settings;

import com.example.solimus.enums.ChargeFrequency;
import com.example.solimus.enums.Currency;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO pour la mise à jour partielle des paramètres financiers du syndic.
 * Tous les champs sont optionnels pour permettre les mises à jour partielles.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateSyndicFinancialSettingsDTO {

    private Currency currency;

    private ChargeFrequency chargeFrequency;

    @DecimalMin(value = "0.0", message = "Le taux de pénalité ne peut pas être négatif")
    @DecimalMax(value = "100.0", message = "Le taux de pénalité ne peut pas dépasser 100%")
    private BigDecimal latePenaltyRate;

    @Min(value = 0, message = "Le délai avant relance ne peut pas être négatif")
    private Integer reminderDelayDays;

    @DecimalMin(value = "0.0", message = "Le pourcentage ne peut pas être négatif")
    @DecimalMax(value = "100.0", message = "Le pourcentage ne peut pas dépasser 100%")
    private BigDecimal reserveFundPercentage;
}

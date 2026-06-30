package com.example.solimus.dtos.syndic.settings;

import com.example.solimus.enums.ChargeFrequency;
import com.example.solimus.enums.Currency;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO utilisé à la fois pour l'affichage et la création/mise à jour
 * des paramètres financiers du syndic (logique create-or-update).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SyndicFinancialSettingsDTO {

    @NotNull(message = "La devise est obligatoire")
    private Currency currency;

    @NotNull(message = "La périodicité des appels de charges est obligatoire")
    private ChargeFrequency chargeFrequency;

    @NotNull(message = "Le taux de pénalité est obligatoire")
    @DecimalMin(value = "0.0", message = "Le taux de pénalité ne peut pas être négatif")
    @DecimalMax(value = "100.0", message = "Le taux de pénalité ne peut pas dépasser 100%")
    private BigDecimal latePenaltyRate;

    @NotNull(message = "Le délai avant relance est obligatoire")
    @Min(value = 0, message = "Le délai avant relance ne peut pas être négatif")
    private Integer reminderDelayDays;

    @NotNull(message = "Le pourcentage du fonds de réserve est obligatoire")
    @DecimalMin(value = "0.0", message = "Le pourcentage ne peut pas être négatif")
    @DecimalMax(value = "100.0", message = "Le pourcentage ne peut pas dépasser 100%")
    private BigDecimal reserveFundPercentage;
}

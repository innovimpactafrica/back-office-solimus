package com.example.solimus.dtos.syndic.wallet;

import com.example.solimus.enums.WithdrawalMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

//DTO d'ajout demande de retrait 

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWithdrawalRequestDTO {

    @NotNull(message = "Le montant est obligatoire")
    @DecimalMin(value = "0.01", message = "Le montant doit être supérieur à 0")
    private BigDecimal amount;

    @NotNull(message = "Le mode de retrait est obligatoire")
    private WithdrawalMode mode;

    @NotNull(message = "La résidence est obligatoire")
    private Long residenceId;

    private Long budgetItemId; // Optionnel : poste budgétaire concerné (uniquement pour les postes sans bien commun)

    @NotBlank(message = "Le compte de réception est obligatoire")
    private String accountNumber; // RIB ou numéro de téléphone

    @NotBlank(message = "Le motif est obligatoire")
    private String reason;
}

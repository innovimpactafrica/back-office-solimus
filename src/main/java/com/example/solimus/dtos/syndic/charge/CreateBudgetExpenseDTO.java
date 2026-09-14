package com.example.solimus.dtos.syndic.charge;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

// DTO d'entrée pour enregistrer une dépense libre sur un poste budgétaire ("Enregistrer une dépense")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBudgetExpenseDTO {

    @NotNull(message = "Le montant est obligatoire")
    @DecimalMin(value = "0.01", message = "Le montant doit être supérieur à 0")
    private BigDecimal amount;

    @NotNull(message = "Le poste budgétaire est obligatoire")
    private Long budgetItemId;

    @NotBlank(message = "La description est obligatoire")
    private String description;

    @NotNull(message = "La date est obligatoire")
    private LocalDate date;

    // Nom du bénéficiaire (optionnel) — texte libre, ex: "Quincaillerie Fall". Affiché dans la
    // colonne "Payeur/Bénéficiaire" de l'historique des transactions.
    private String beneficiaryName;
}

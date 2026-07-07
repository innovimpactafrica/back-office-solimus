package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.util.List;

//DTO de réponse principale
@Data
public class BudgetListResponse {
    private Integer totalBudgets; // Nombre total de budgets du syndic (toutes années confondues, indépendant de la pagination)
    private Integer activeBudgetsCount; // Nombre de budgets ACTIVE (indépendant de la pagination)
    private List<BudgetCardDTO> budgets; // Budgets affichés sur la page courante
    private Integer currentPage; // Numéro de la page actuelle (0-indexé)
    private Integer totalPages; // Nombre total de pages
}
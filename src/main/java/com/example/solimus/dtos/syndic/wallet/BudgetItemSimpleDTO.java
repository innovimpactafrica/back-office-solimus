package com.example.solimus.dtos.syndic.wallet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetItemSimpleDTO {
    private Long id;
    private String libelle;
}

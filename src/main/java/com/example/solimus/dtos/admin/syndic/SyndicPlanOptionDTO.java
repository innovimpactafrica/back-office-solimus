package com.example.solimus.dtos.admin.syndic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO minimal — liste déroulante "Formule" du formulaire "Nouveau syndic" (admin)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SyndicPlanOptionDTO {

    private Long id;
    private String name;
}

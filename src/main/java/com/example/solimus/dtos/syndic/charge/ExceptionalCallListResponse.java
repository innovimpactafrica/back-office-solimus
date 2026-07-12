package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.util.List;

//DTO de réponse principale — liste des appels exceptionnels du syndic
@Data
public class ExceptionalCallListResponse {
    private Integer totalExceptionalCalls; // Nombre total d'appels exceptionnels
    private List<ExceptionalCallCardDTO> exceptionalCalls; // Appels affichés sur la page courante
    private Integer currentPage; // Numéro de la page actuelle
    private Integer totalPages; // Nombre total de pages
}

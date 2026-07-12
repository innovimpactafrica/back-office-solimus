package com.example.solimus.dtos.owner.charge;

import lombok.Builder;
import lombok.Data;
import java.util.List;

//DTO de réponse principale — liste des charges du copropriétaire
@Data
@Builder
public class MyChargeListResponse {
    private MyChargesSummaryDTO summary; // Bandeau résumé (total à payer, nombre en attente, prochaine échéance)
    private List<MyChargeCardDTO> charges; // Charges affichées sur la page courante
    private Integer currentPage; // Numéro de la page actuelle
    private Integer totalPages; // Nombre total de pages
    private Integer totalElements; // Nombre total d'éléments
}

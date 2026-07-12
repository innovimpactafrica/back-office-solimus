package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.util.List;

//DTO de réponse principale — liste des appels de charges du syndic
@Data
public class ChargeCallListResponse {
    private Integer totalChargeCalls; // Nombre total d'appels de charges du syndic
    private List<ChargeCallCardDTO> chargeCalls; // Appels affichés sur la page courante
    private Integer currentPage; // Numéro de la page actuelle
    private  Integer totalPages; // Nombre total de pages
}

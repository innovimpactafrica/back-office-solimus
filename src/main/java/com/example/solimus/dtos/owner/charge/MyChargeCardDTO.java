package com.example.solimus.dtos.owner.charge;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

//DTO d'une carte de charge dans la liste "Mes charges"
@Data
@Builder
public class MyChargeCardDTO {
    private Long id; // ID de l'item (ChargeCallItem ou ExceptionalCallItem)
    private String type; // "REGULAR" ou "EXCEPTIONAL"
    private String typeLabel; // Affichage : "Charge Courante" ou "Charge Exceptionnelle  
    private String title; // "Charges mensuelles", "Fonds de travaux"...
    private String residenceName;
    private Long residenceId; // Utilisé pour le filtre, pas forcément affiché à l'écran
    private String propertyReference; // numéro bien
    private BigDecimal remainingAmount; // Solde restant à payer
    private LocalDate dueDate;
    private String status; // "En attente", "Payé", "Partiel"
}

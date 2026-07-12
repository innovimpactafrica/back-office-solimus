package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;

//DTO d'un point du graphique "Encaissement mensuel" (Prévu vs Encaissé)
@Data
public class MonthlyCollectionDTO {
    private String monthLabel; // Ex: "Jan", "Fév"...
    private BigDecimal expected; // Montant prévu ce mois-là
    private BigDecimal collected; // Montant réellement encaissé ce mois-là
}
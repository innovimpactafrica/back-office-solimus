package com.example.solimus.dtos.provider.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;


//DTO regroupant le total de demandes reçues et la page des demandes filtrées
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderRequestsDTO {

    // Le total de TOUTES les demandes reçues, peu importe le filtre actif
    private long totalReceivedRequests;

    // La page actuelle de résultats, qui elle DÉPEND du filtre sélectionné par l'utilisateur
    private Page<ProviderRequestSummaryDTO> requests;

    // Accès aux demandes : wallet strictement supérieur au seuil. false -> 'requests' est vide.
    private boolean eligible;
    private java.math.BigDecimal walletBalance;
    private java.math.BigDecimal minimumWalletBalanceExclusive;
    private String eligibilityMessage;

    // Tri appliqué (DISTANCE par défaut : la plus proche d'abord)
    private com.example.solimus.enums.ProviderRequestSort sortBy;
}

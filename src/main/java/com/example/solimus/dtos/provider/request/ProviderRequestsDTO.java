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
}

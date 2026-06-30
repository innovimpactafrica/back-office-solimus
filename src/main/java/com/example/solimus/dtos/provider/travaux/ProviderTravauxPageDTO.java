package com.example.solimus.dtos.provider.travaux;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

// Réponse de l'onglet "Travaux" du prestataire : compteur "en cours" + liste paginée
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderTravauxPageDTO {

    // Nombre de travaux actuellement en cours (affiché en en-tête)
    private long pendingCount;

    // Liste paginée des travaux du prestataire
    private Page<ProviderTravauxDTO> works;
}

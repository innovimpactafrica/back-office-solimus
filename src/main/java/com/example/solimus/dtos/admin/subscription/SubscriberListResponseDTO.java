package com.example.solimus.dtos.admin.subscription;

import lombok.*;
import java.util.List;

//===== DTO RÉPONSE - LISTE DES ABONNÉS (Syndic + Prestataire fusionnés) =====
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SubscriberListResponseDTO {

    private long totalCount;
    private List<SubscriberRowDTO> subscribers;
    private long currentPage;
    private long totalPages;
}
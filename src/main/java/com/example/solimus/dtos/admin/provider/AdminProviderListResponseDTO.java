package com.example.solimus.dtos.admin.provider;

import lombok.*;

import java.util.List;

// ===== DTO — Réponse paginée de la liste "Admin > Prestataires" =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminProviderListResponseDTO {

    private long totalCount;
    private List<AdminProviderRowDTO> providers;
    private int currentPage;
    private int totalPages;
}

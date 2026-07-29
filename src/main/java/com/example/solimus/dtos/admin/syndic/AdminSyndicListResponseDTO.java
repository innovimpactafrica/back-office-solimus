package com.example.solimus.dtos.admin.syndic;

import lombok.*;

import java.util.List;

// ===== DTO — Réponse paginée de la liste "Admin > Syndics" =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSyndicListResponseDTO {

    private long totalCount;
    private List<AdminSyndicRowDTO> syndics;
    private int currentPage;
    private int totalPages;
}

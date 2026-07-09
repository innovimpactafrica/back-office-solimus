package com.example.solimus.dtos.syndic.signalement;

import lombok.Builder;
import lombok.Data;

import java.util.List;

//DTO de réponse principale — liste paginée des signalements côté syndic
@Data
@Builder
public class SyndicSignalementListResponse {
    private List<SyndicSignalementCardDTO> signalements;
    private int currentPage;
    private int totalPages;
    private long totalElements;
}
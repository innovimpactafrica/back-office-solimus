package com.example.solimus.dtos.syndic.travaux;

import lombok.Builder;
import lombok.Data;

import java.util.List;

//DTO de réponse principale — liste paginée des incidents travaux (syndic)
@Data
@Builder
public class SyndicTravauxListResponse {
    private List<SyndicTravauxCardDTO> incidents;
    private int currentPage;
    private int totalPages;
    private long totalElements;
}

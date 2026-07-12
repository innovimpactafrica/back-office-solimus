package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

//DTO de réponse principale — liste des impayés du syndic
@Data
public class UnpaidListResponse {
    private Integer unpaidCoOwnersCount;
    private BigDecimal totalUnpaidAmount;
    private List<UnpaidRowDTO> unpaidItems;
    private Integer currentPage;
    private Integer totalPages;
}

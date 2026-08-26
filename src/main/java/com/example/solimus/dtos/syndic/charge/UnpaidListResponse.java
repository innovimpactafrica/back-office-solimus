package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

//DTO de réponse principale — liste des impayés du syndic
@Data
public class UnpaidListResponse {
    private Integer unpaidItemsCount; // nb de LIGNES impayées (ChargeCallItem) — un même copropriétaire peut en avoir plusieurs
    private Integer distinctUnpaidCoOwnersCount; // nb de copropriétaires distincts concernés par au moins un impayé
    private BigDecimal totalUnpaidAmount;
    private List<UnpaidRowDTO> unpaidItems;
    private Integer currentPage;
    private Integer totalPages;
}
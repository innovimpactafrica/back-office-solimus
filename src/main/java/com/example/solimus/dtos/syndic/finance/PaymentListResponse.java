package com.example.solimus.dtos.syndic.finance;

import lombok.Data;
import java.util.List;

//DTO de réponse principale — liste des paiements du syndic (bouton "Paiements")
@Data
public class PaymentListResponse {
    private Integer totalPayments;
    private List<FinancePaymentRowDTO> payments;
    private Integer currentPage;
    private Integer totalPages;
}

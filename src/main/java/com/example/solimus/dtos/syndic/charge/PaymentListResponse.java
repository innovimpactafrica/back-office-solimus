package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.util.List;

//DTO de réponse principale — liste des paiements du syndic
@Data
public class PaymentListResponse {
    private Integer totalPayments;
    private List<PaymentRowDTO> payments;
    private Integer currentPage;
    private Integer totalPages;
}

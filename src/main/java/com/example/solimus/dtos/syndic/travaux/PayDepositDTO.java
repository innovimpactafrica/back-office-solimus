package com.example.solimus.dtos.syndic.travaux;

import com.example.solimus.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

//DTO pour payer un acompte
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayDepositDTO {
    private BigDecimal montant;
    private PaymentMethod methode;
}

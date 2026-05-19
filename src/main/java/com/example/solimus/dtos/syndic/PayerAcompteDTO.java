package com.example.solimus.dtos.syndic;

import com.example.solimus.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayerAcompteDTO {
    private BigDecimal montant;
    private PaymentMethod methode;
}

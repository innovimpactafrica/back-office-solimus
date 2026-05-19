package com.example.solimus.dtos.syndic;

import com.example.solimus.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValiderTravauxDTO {
    private PaymentMethod methode;
}

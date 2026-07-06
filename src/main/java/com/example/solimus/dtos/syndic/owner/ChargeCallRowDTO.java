package com.example.solimus.dtos.syndic.owner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

//DTO pour une ligne d'appel de charges (tableau)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChargeCallRowDTO {

    private String reference;
    private LocalDateTime date;
    private BigDecimal amount;
    private String status;
}

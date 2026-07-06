package com.example.solimus.dtos.charge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionalCallItemDTO {

    private Long id;
    private Long coOwnerId;
    private String coOwnerName;
    private BigDecimal tantieme;
    private BigDecimal quotePart;
    private BigDecimal paidAmount;
}

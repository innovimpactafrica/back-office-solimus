package com.example.solimus.dtos.syndic.charge;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class ExceptionalCallItemDTO {
    private Long id;
    private Long coOwnerId;
    private String coOwnerName;
    private BigDecimal tantieme;
    private BigDecimal quotePart;
    private BigDecimal paidAmount;
}

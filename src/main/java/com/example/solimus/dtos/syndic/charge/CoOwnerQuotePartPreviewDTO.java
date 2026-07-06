package com.example.solimus.dtos.syndic.charge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoOwnerQuotePartPreviewDTO {

    private Long coOwnerId;
    private String coOwnerName;
    private BigDecimal tantieme;
    private BigDecimal quotePart;
}

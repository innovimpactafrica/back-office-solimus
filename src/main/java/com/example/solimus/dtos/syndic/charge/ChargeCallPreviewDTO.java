package com.example.solimus.dtos.syndic.charge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargeCallPreviewDTO {

    private String budgetReference;
    private String residenceName;
    private Integer year;
    private Integer periodNumber;
    private BigDecimal totalAmount;
    private LocalDate sentDate;
    private LocalDate dueDate;
    private List<CoOwnerQuotePartPreviewDTO> repartition;
    private BigDecimal totalTantieme;
    private Integer coOwnersCount;
}

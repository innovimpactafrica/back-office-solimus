package com.example.solimus.dtos.dashboard;

import com.example.solimus.enums.ChargeStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargeAllocationSummaryDTO {

    private Long id;
    private String title;
    private BigDecimal amount;

    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate dueDate;

    private ChargeStatus status;
    private String typeBien;
    private String residenceName;
}

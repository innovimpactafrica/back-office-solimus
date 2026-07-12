package com.example.solimus.dtos.syndic.charge;

import com.example.solimus.enums.RepartitionMode;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class UpdateExceptionalCallFinancialDTO {
    private BigDecimal totalAmount;
    private RepartitionMode repartitionMode;
    private List<CustomCoOwnerAmountDTO> customAmounts;
}

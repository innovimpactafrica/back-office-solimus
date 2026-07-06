package com.example.solimus.dtos.charge;

import com.example.solimus.enums.RepartitionMode;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateExceptionalCallFinancialDTO {

    @NotNull(message = "Le montant total est obligatoire")
    private java.math.BigDecimal totalAmount;

    @NotNull(message = "Le type de répartition est obligatoire")
    private RepartitionMode repartitionMode;

    // Obligatoire UNIQUEMENT si repartitionMode == CUSTOM — ignoré si OWNERSHIP_SHARES
    private List<CustomCoOwnerAmountDTO> customAmounts;
}

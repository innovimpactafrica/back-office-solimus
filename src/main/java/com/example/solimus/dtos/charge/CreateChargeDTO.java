package com.example.solimus.dtos.charge;

import com.example.solimus.enums.ChargeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// DTO pour créer une charge
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateChargeDTO {

    @NotBlank
    private String title;

    private String description;

    @NotNull
    private ChargeType type;

    @NotNull
    private BigDecimal totalAmount;

    private String period;           // "Juin 2026"
    private LocalDate dueDate;

    @NotNull
    private Long residenceId;

    // Répartition des frais
    private List<CreateChargeLineDTO> lines;

    // Documents joints
    private List<String> documentUrls;
}

package com.example.solimus.dtos.charge;

import com.example.solimus.enums.ChargeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Réponse après création d'une charge
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargeResponseDTO {
    private Long id;
    private String reference;
    private String title;
    private ChargeType type;
    private BigDecimal totalAmount;
    private String period;
    private LocalDate dueDate;
    private String residenceName;
    private int nombreAllocations;   // combien de copros concernés
    private List<ChargeLineDTO> lines;
    private List<String> documentUrls;
    private LocalDateTime createdAt;
}

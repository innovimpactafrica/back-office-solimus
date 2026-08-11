package com.example.solimus.dtos.syndic.charge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aperçu de la répartition d'un appel de charges exceptionnel pas encore créé — écran
 * "Nouvel Appel Exceptionnel". Même structure de ligne (CoOwnerQuotePartPreviewDTO) que celle
 * déjà utilisée par previewChargeCallByResidence, pour rester cohérent avec les charges courantes.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExceptionalCallPreviewDTO {

    private Long residenceId;
    private String residenceName;
    private BigDecimal totalAmount;
    private BigDecimal totalTantieme;
    private Integer coOwnersCount;
    private List<CoOwnerQuotePartPreviewDTO> repartition;
}

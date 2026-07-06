package com.example.solimus.dtos.charge;

import com.example.solimus.enums.ExceptionalCallCategory;
import com.example.solimus.enums.ExceptionalCallStatus;
import com.example.solimus.enums.RepartitionMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionalCallDetailDTO {

    private Long id;
    private Long residenceId;
    private String residenceName;
    private Long syndicId;
    private String syndicName;

    // Section 1
    private ExceptionalCallCategory category;
    private String title;
    private String description;

    // Section 2
    private BigDecimal totalAmount;
    private RepartitionMode repartitionMode;
    private List<ExceptionalCallItemDTO> items;

    // Section 3
    private Boolean requiresAgValidation;
    private ExceptionalCallStatus status;
    private List<ExceptionalCallDocumentDTO> documents;

    private LocalDateTime createdAt;
}

package com.example.solimus.dtos.syndic.charge;

import com.example.solimus.enums.ExceptionalCallCategory;
import com.example.solimus.enums.ExceptionalCallStatus;
import com.example.solimus.enums.RepartitionMode;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ExceptionalCallDetailDTO {
    private Long id;
    private Long residenceId;
    private String residenceName;
    private Long syndicId;
    private String syndicName;
    private ExceptionalCallCategory category;
    private String title;
    private String description;
    private BigDecimal totalAmount;
    private RepartitionMode repartitionMode;
    private List<ExceptionalCallItemDTO> items;
    private Boolean requiresAgValidation;
    private ExceptionalCallStatus status;
    private List<ExceptionalCallDocumentDTO> documents;
    private LocalDateTime createdAt;
}

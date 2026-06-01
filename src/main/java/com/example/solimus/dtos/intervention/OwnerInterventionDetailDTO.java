package com.example.solimus.dtos.intervention;

import com.example.solimus.enums.InterventionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerInterventionDetailDTO {

    private Long id;
    private String title;
    private String description;
    private String residenceName;
    private String propertyReference;
    private InterventionStatus status;
    private String specialtyName;
    private List<String> photoUrls;
    private ProviderInfoDTO selectedProvider;
    private List<OwnerTimelineStepDTO> timeline;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}

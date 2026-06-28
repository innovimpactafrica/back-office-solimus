package com.example.solimus.dtos.provider.request;

import com.example.solimus.dtos.intervention.InterventionRequestSummaryDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderRequestsPageDTO {
    private long totalRequests;  // total des demandes reçues
    private Page<InterventionRequestSummaryDTO> requests;
}

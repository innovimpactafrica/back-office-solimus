package com.example.solimus.dtos.syndic.travaux;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SendToProvidersStatusDTO {
    private Long interventionId;
    private boolean sentToProviders;
    private int notifiedProvidersCount;
    private boolean canSend;
    private String message;
}

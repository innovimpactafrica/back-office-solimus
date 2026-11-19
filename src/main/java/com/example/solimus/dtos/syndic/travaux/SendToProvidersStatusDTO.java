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
    private Double broadcastRadiusKm;    // rayon atteint par la diffusion progressive (km)
    private Double maxBroadcastRadiusKm; // rayon maximal de diffusion (km)
    private boolean canSend;
    private String message;
}

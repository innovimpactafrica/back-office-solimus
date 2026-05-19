package com.example.solimus.dtos.provider;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderProfileDTO {

    // Infos affichées en haut
    private String companyName;        // "Sen Plomberie"
    private String specialtyName;      // "Plomberie"
    private boolean available;         // point vert/rouge (actif ou inactif)
    private String email;
    private String phone;
    private String language;           // "Français, Wolof"
    
    @JsonFormat(pattern = "MMMM yyyy", locale = "fr") // "Janvier 2025" par ex
    private LocalDateTime memberSince; // quand est-ce qu'il s'est inscrit
    
    private String profilePhotoUrl;
}

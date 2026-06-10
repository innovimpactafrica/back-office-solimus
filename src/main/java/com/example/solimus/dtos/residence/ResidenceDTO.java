package com.example.solimus.dtos.residence;

import com.example.solimus.enums.ResidenceHealthStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO pour la réponse de la résidence
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResidenceDTO {
    private Long id;
    private String name;
    private String description;
    private String fullAddress;
    private String city;
    private String country;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String photoUrl;
    private Integer lotsCount;
    private Integer constructionYear;
    private Integer renovationYear;
    private BigDecimal annualBudget;
    private ResidenceHealthStatus healthStatus;
    private Long syndicId;
    private String syndicName;
    private Integer totalCoproprietaires;
    private Integer incidentsOuverts;
    private Double tauxImpayes;
    private BigDecimal tresorerie;
    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime updatedAt;
}

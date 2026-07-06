package com.example.solimus.dtos.charge;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinalizeExceptionalCallDTO {
    @NotNull private Boolean requiresAgValidation;
}

package com.example.solimus.dtos.syndic.charge;

import com.example.solimus.enums.ExceptionalCallCategory;
import com.example.solimus.enums.RepartitionMode;
import lombok.Data;
import java.util.List;

@Data
public class CreateExceptionalCallDTO {
    private Long residenceId;
    private ExceptionalCallCategory category;
    private String title;
    private String description;
    private RepartitionMode repartitionMode;
    private List<CustomCoOwnerAmountDTO> customAmounts;
}

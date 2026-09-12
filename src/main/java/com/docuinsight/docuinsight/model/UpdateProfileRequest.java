package com.docuinsight.docuinsight.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @NotBlank(message = "Name cannot be blank")
    private String name;

}

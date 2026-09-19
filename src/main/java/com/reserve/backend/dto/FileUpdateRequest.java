package com.reserve.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FileUpdateRequest {
    @NotBlank(message = "Filename is required")
    private String name;
}

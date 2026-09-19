package com.reserve.backend.dto;

import com.reserve.backend.entity.StorageType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FileUpdateRequest {
    @NotBlank(message = "Filename is required")
    private String name;
    private StorageType storageType;
}


package com.reserve.backend.dto;

import com.reserve.backend.entity.StorageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileResponse {
    private Long id;
    private String originalFilename;
    private String storedFilename;
    private String mimeType;
    private Long fileSize;
    private String cloudinaryUrl;
    private String cloudinaryPublicId;
    private String resourceType;
    private StorageType storageType;
    private Long folderId;
    private Long ownerId;
    private String ownerName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

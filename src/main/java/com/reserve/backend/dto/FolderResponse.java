package com.reserve.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FolderResponse {
    private Long id;
    private String name;
    private Long parentId;
    private String parentName;
    private com.reserve.backend.entity.Visibility visibility;
    private Boolean isOwner;
    private List<FolderResponse> subFolders;
    private List<FileResponse> files;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

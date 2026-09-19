package com.reserve.backend.service;

import com.reserve.backend.dto.*;
import com.reserve.backend.entity.FileItem;
import com.reserve.backend.entity.Folder;
import com.reserve.backend.entity.User;
import com.reserve.backend.exception.BadRequestException;
import com.reserve.backend.exception.ResourceNotFoundException;
import com.reserve.backend.repository.FolderRepository;
import com.reserve.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final AuthService authService;
    private final CloudinaryService cloudinaryService;

    @Transactional
    public FolderResponse createFolder(FolderRequest request, UserPrincipal currentUserPrincipal) {
        User currentUser = authService.getCurrentUserEntity(currentUserPrincipal);

        Folder parentFolder = null;
        if (request.getParentId() != null) {
            parentFolder = folderRepository.findByIdAndUserId(request.getParentId(), currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent folder not found or access denied"));
        }

        boolean exists = parentFolder == null ?
                folderRepository.existsByNameAndUserIdAndParentFolderIsNull(request.getName(), currentUser.getId()) :
                folderRepository.existsByNameAndUserIdAndParentFolder(request.getName(), currentUser.getId(), parentFolder);

        if (exists) {
            throw new BadRequestException("A folder with this name already exists in this location");
        }

        com.reserve.backend.entity.Visibility visibility = "PUBLIC".equalsIgnoreCase(request.getVisibility()) 
                ? com.reserve.backend.entity.Visibility.PUBLIC 
                : com.reserve.backend.entity.Visibility.PRIVATE;

        Folder folder = Folder.builder()
                .name(request.getName())
                .user(currentUser)
                .parentFolder(parentFolder)
                .visibility(visibility)
                .build();

        Folder saved = folderRepository.save(folder);
        return mapToFolderResponse(saved, false, currentUserPrincipal);
    }

    @Transactional(readOnly = true)
    public List<FolderResponse> getUserFolders(Long parentId, String keyword, Integer order, Integer page, Integer limit, UserPrincipal currentUserPrincipal) {
        Long userId = currentUserPrincipal.getId();
        List<Folder> folders;
        if (parentId == null) {
            folders = folderRepository.findByUserIdAndParentFolderIsNullOrderByNameAsc(userId);
        } else {
            folders = folderRepository.findByUserIdAndParentFolderIdOrderByNameAsc(userId, parentId);
        }

        List<FolderResponse> responseList = folders.stream()
                .map(f -> mapToFolderResponse(f, false, currentUserPrincipal))
                .filter(f -> !org.springframework.util.StringUtils.hasText(keyword) || f.getName().toLowerCase().contains(keyword.toLowerCase().trim()))
                .collect(Collectors.toList());

        sortFolders(responseList, order);
        return paginateList(responseList, page, limit);
    }

    private void sortFolders(List<FolderResponse> list, Integer order) {
        if (order == null) order = 3;
        switch (order) {
            case 1: // Name ASC
                list.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                break;
            case 2: // Name DESC
                list.sort((a, b) -> b.getName().compareToIgnoreCase(a.getName()));
                break;
            case 4: // Date ASC
                list.sort((a, b) -> {
                    if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
                    return a.getCreatedAt().compareTo(b.getCreatedAt());
                });
                break;
            case 3: // Date DESC (Default)
            default:
                list.sort((a, b) -> {
                    if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                });
                break;
        }
    }

    private <T> List<T> paginateList(List<T> list, Integer page, Integer limit) {
        if (page == null || page < 1) page = 1;
        if (limit == null || limit < 1) limit = 20;

        int fromIndex = (page - 1) * limit;
        if (fromIndex >= list.size()) {
            return List.of();
        }
        int toIndex = Math.min(fromIndex + limit, list.size());
        return list.subList(fromIndex, toIndex);
    }

    @Transactional(readOnly = true)
    public FolderResponse getFolderDetails(Long folderId, UserPrincipal currentUserPrincipal) {
        Long userId = currentUserPrincipal.getId();
        Folder folder = folderRepository.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found or access denied"));

        return mapToFolderResponse(folder, true, currentUserPrincipal);
    }

    @Transactional(readOnly = true)
    public FolderResponse getPublicFolderDetails(Long folderId, UserPrincipal currentUserPrincipal) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        boolean isOwner = currentUserPrincipal != null && folder.getUser().getId().equals(currentUserPrincipal.getId());
        if (folder.getVisibility() != com.reserve.backend.entity.Visibility.PUBLIC && !isOwner) {
            throw new ResourceNotFoundException("Folder not found or is private");
        }

        return mapToFolderResponse(folder, true, currentUserPrincipal);
    }

    @Transactional
    public FolderResponse renameFolder(Long folderId, FolderRequest request, UserPrincipal currentUserPrincipal) {
        Long userId = currentUserPrincipal.getId();
        Folder folder = folderRepository.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found or access denied"));

        String newName = request.getName().trim();
        if (newName.isEmpty()) {
            throw new BadRequestException("Folder name cannot be empty");
        }

        folder.setName(newName);
        if (request.getVisibility() != null) {
            folder.setVisibility("PUBLIC".equalsIgnoreCase(request.getVisibility()) ? com.reserve.backend.entity.Visibility.PUBLIC : com.reserve.backend.entity.Visibility.PRIVATE);
        }

        Folder updated = folderRepository.save(folder);
        return mapToFolderResponse(updated, false, currentUserPrincipal);
    }

    @Transactional
    public FolderResponse moveFolder(Long folderId, FolderMoveRequest request, UserPrincipal currentUserPrincipal) {
        Long userId = currentUserPrincipal.getId();
        Folder folder = folderRepository.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found or access denied"));

        Folder targetParent = null;
        if (request.getTargetParentId() != null) {
            if (request.getTargetParentId().equals(folderId)) {
                throw new BadRequestException("Cannot move a folder into itself");
            }

            targetParent = folderRepository.findByIdAndUserId(request.getTargetParentId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Target folder not found or access denied"));

            Folder current = targetParent;
            while (current != null) {
                if (current.getId().equals(folderId)) {
                    throw new BadRequestException("Cannot move a folder into one of its subfolders");
                }
                current = current.getParentFolder();
            }
        }

        folder.setParentFolder(targetParent);
        Folder updated = folderRepository.save(folder);
        return mapToFolderResponse(updated, false, currentUserPrincipal);
    }

    @Transactional
    public void deleteFolder(Long folderId, UserPrincipal currentUserPrincipal) {
        Long userId = currentUserPrincipal.getId();
        Folder folder = folderRepository.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found or access denied"));

        deleteFolderCloudinaryResources(folder);
        folderRepository.delete(folder);
    }

    private void deleteFolderCloudinaryResources(Folder folder) {
        if (folder.getFiles() != null) {
            for (FileItem file : folder.getFiles()) {
                cloudinaryService.deleteFile(file.getCloudinaryPublicId(), file.getResourceType());
            }
        }
        if (folder.getSubFolders() != null) {
            for (Folder sub : folder.getSubFolders()) {
                deleteFolderCloudinaryResources(sub);
            }
        }
    }

    public FolderResponse mapToFolderResponse(Folder folder, boolean includeContents, UserPrincipal currentUserPrincipal) {
        boolean isOwner = currentUserPrincipal != null && folder.getUser() != null && folder.getUser().getId().equals(currentUserPrincipal.getId());

        FolderResponse response = FolderResponse.builder()
                .id(folder.getId())
                .name(folder.getName())
                .parentId(folder.getParentFolder() != null ? folder.getParentFolder().getId() : null)
                .parentName(folder.getParentFolder() != null ? folder.getParentFolder().getName() : null)
                .visibility(folder.getVisibility())
                .isOwner(isOwner)
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .build();

        if (includeContents) {
            if (folder.getSubFolders() != null) {
                response.setSubFolders(folder.getSubFolders().stream()
                        .map(sf -> mapToFolderResponse(sf, false, currentUserPrincipal))
                        .collect(Collectors.toList()));
            }
            if (folder.getFiles() != null) {
                response.setFiles(folder.getFiles().stream()
                        .map(this::mapToFileResponse)
                        .collect(Collectors.toList()));
            }
        }
        return response;
    }

    private FileResponse mapToFileResponse(FileItem file) {
        return FileResponse.builder()
                .id(file.getId())
                .originalFilename(file.getOriginalFilename())
                .storedFilename(file.getStoredFilename())
                .mimeType(file.getMimeType())
                .fileSize(file.getFileSize())
                .cloudinaryUrl(file.getCloudinaryUrl())
                .cloudinaryPublicId(file.getCloudinaryPublicId())
                .resourceType(file.getResourceType())
                .storageType(file.getStorageType())
                .folderId(file.getFolder() != null ? file.getFolder().getId() : null)
                .ownerId(file.getUser() != null ? file.getUser().getId() : null)
                .ownerName(file.getUser() != null ? file.getUser().getName() : "Public Uploader")
                .createdAt(file.getCreatedAt())
                .updatedAt(file.getUpdatedAt())
                .build();
    }
}

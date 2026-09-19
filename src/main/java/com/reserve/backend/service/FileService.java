package com.reserve.backend.service;

import com.reserve.backend.dto.FileMoveRequest;
import com.reserve.backend.dto.FileResponse;
import com.reserve.backend.dto.FileUpdateRequest;
import com.reserve.backend.entity.FileItem;
import com.reserve.backend.entity.Folder;
import com.reserve.backend.entity.PublicUpload;
import com.reserve.backend.entity.StorageType;
import com.reserve.backend.entity.User;
import com.reserve.backend.exception.BadRequestException;
import com.reserve.backend.exception.ResourceNotFoundException;
import com.reserve.backend.repository.FileItemRepository;
import com.reserve.backend.repository.FolderRepository;
import com.reserve.backend.repository.PublicUploadRepository;
import com.reserve.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    private final FileItemRepository fileItemRepository;
    private final PublicUploadRepository publicUploadRepository;
    private final FolderRepository folderRepository;
    private final AuthService authService;
    private final CloudinaryService cloudinaryService;

    @Transactional
    public FileResponse uploadPrivateFile(MultipartFile file, Long folderId, UserPrincipal currentUserPrincipal) {
        return uploadFileInternal(file, folderId, StorageType.PRIVATE, currentUserPrincipal);
    }

    @Transactional
    public FileResponse uploadSharedFile(MultipartFile file, UserPrincipal currentUserPrincipal) {
        // Public uploads stored in dedicated public_uploads table with no userId
        Map<String, Object> uploadResult = cloudinaryService.uploadFile(file);

        String cloudinaryUrl = (String) uploadResult.get("secure_url");
        String publicId = (String) uploadResult.get("public_id");
        String resourceType = (String) uploadResult.get("resource_type");
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed_file");

        PublicUpload publicUpload = PublicUpload.builder()
                .originalFilename(originalFilename)
                .storedFilename(publicId)
                .mimeType(file.getContentType())
                .fileSize(file.getSize())
                .cloudinaryUrl(cloudinaryUrl)
                .cloudinaryPublicId(publicId)
                .resourceType(resourceType)
                .build();

        PublicUpload saved = publicUploadRepository.save(publicUpload);
        return mapPublicUploadToFileResponse(saved);
    }

    private FileResponse uploadFileInternal(MultipartFile file, Long folderId, StorageType storageType, UserPrincipal currentUserPrincipal) {
        User currentUser = currentUserPrincipal != null ? authService.getCurrentUserEntity(currentUserPrincipal) : null;

        Folder folder = null;
        if (folderId != null && storageType == StorageType.PRIVATE && currentUser != null) {
            folder = folderRepository.findByIdAndUserId(folderId, currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found or access denied"));
        }

        Map<String, Object> uploadResult = cloudinaryService.uploadFile(file);

        String cloudinaryUrl = (String) uploadResult.get("secure_url");
        String publicId = (String) uploadResult.get("public_id");
        String resourceType = (String) uploadResult.get("resource_type");
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed_file");

        FileItem fileItem = FileItem.builder()
                .originalFilename(originalFilename)
                .storedFilename(publicId)
                .mimeType(file.getContentType())
                .fileSize(file.getSize())
                .cloudinaryUrl(cloudinaryUrl)
                .cloudinaryPublicId(publicId)
                .resourceType(resourceType)
                .storageType(storageType)
                .folder(folder)
                .user(currentUser)
                .build();

        FileItem saved = fileItemRepository.save(fileItem);
        return mapToFileResponse(saved);
    }

    @Transactional
    public FileResponse uploadWebSocketBytes(byte[] fileBytes, String originalFilename, String mimeType, Long folderId, boolean isShared, UserPrincipal currentUserPrincipal) {
        Map<String, Object> uploadResult = cloudinaryService.uploadFileBytes(fileBytes, originalFilename, mimeType);

        String cloudinaryUrl = (String) uploadResult.get("secure_url");
        String publicId = (String) uploadResult.get("public_id");
        String resourceType = (String) uploadResult.get("resource_type");
        String cleanName = StringUtils.cleanPath(originalFilename != null ? originalFilename : "unnamed_file");

        // If public/shared upload or user is unauthenticated, save to dedicated public_uploads table (NO userId)
        if (isShared || currentUserPrincipal == null) {
            PublicUpload publicUpload = PublicUpload.builder()
                    .originalFilename(cleanName)
                    .storedFilename(publicId)
                    .mimeType(mimeType != null ? mimeType : "application/octet-stream")
                    .fileSize((long) fileBytes.length)
                    .cloudinaryUrl(cloudinaryUrl)
                    .cloudinaryPublicId(publicId)
                    .resourceType(resourceType)
                    .build();

            PublicUpload saved = publicUploadRepository.save(publicUpload);
            return mapPublicUploadToFileResponse(saved);
        }

        // Private user upload in files table
        User currentUser = authService.getCurrentUserEntity(currentUserPrincipal);
        Folder folder = null;
        if (folderId != null) {
            folder = folderRepository.findByIdAndUserId(folderId, currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found or access denied"));
        }

        FileItem fileItem = FileItem.builder()
                .originalFilename(cleanName)
                .storedFilename(publicId)
                .mimeType(mimeType != null ? mimeType : "application/octet-stream")
                .fileSize((long) fileBytes.length)
                .cloudinaryUrl(cloudinaryUrl)
                .cloudinaryPublicId(publicId)
                .resourceType(resourceType)
                .storageType(StorageType.PRIVATE)
                .folder(folder)
                .user(currentUser)
                .build();

        FileItem saved = fileItemRepository.save(fileItem);
        return mapToFileResponse(saved);
    }

    @Transactional
    public FileResponse copySharedFileToPrivate(Long fileId, FileMoveRequest request, UserPrincipal currentUserPrincipal) {
        User currentUser = authService.getCurrentUserEntity(currentUserPrincipal);

        Folder targetFolder = null;
        if (request != null && request.getTargetFolderId() != null) {
            targetFolder = folderRepository.findByIdAndUserId(request.getTargetFolderId(), currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Target folder not found or access denied"));
        }

        // Check public_uploads table first
        PublicUpload publicUpload = publicUploadRepository.findById(fileId).orElse(null);
        if (publicUpload != null) {
            FileItem copiedFile = FileItem.builder()
                    .originalFilename(publicUpload.getOriginalFilename())
                    .storedFilename(publicUpload.getStoredFilename())
                    .mimeType(publicUpload.getMimeType())
                    .fileSize(publicUpload.getFileSize())
                    .cloudinaryUrl(publicUpload.getCloudinaryUrl())
                    .cloudinaryPublicId(publicUpload.getCloudinaryPublicId())
                    .resourceType(publicUpload.getResourceType())
                    .storageType(StorageType.PRIVATE)
                    .folder(targetFolder)
                    .user(currentUser)
                    .build();

            FileItem saved = fileItemRepository.save(copiedFile);
            return mapToFileResponse(saved);
        }

        // Fallback to files table
        FileItem sharedFile = fileItemRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        FileItem copiedFile = FileItem.builder()
                .originalFilename(sharedFile.getOriginalFilename())
                .storedFilename(sharedFile.getStoredFilename())
                .mimeType(sharedFile.getMimeType())
                .fileSize(sharedFile.getFileSize())
                .cloudinaryUrl(sharedFile.getCloudinaryUrl())
                .cloudinaryPublicId(sharedFile.getCloudinaryPublicId())
                .resourceType(sharedFile.getResourceType())
                .storageType(StorageType.PRIVATE)
                .folder(targetFolder)
                .user(currentUser)
                .build();

        FileItem saved = fileItemRepository.save(copiedFile);
        return mapToFileResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<FileResponse> getUserPrivateFiles(Long folderId, String search, String keyword, Integer order, Integer page, Integer limit, UserPrincipal currentUserPrincipal) {
        Long userId = currentUserPrincipal.getId();
        String query = StringUtils.hasText(search) ? search : keyword;

        List<FileItem> files;
        if (StringUtils.hasText(query)) {
            files = fileItemRepository.searchPrivateFiles(userId, StorageType.PRIVATE, query.trim());
        } else if (folderId != null) {
            files = fileItemRepository.findByUserIdAndStorageTypeAndFolderIdOrderByOriginalFilenameAsc(userId, StorageType.PRIVATE, folderId);
        } else {
            files = fileItemRepository.findByUserIdAndStorageTypeAndFolderIsNullOrderByOriginalFilenameAsc(userId, StorageType.PRIVATE);
        }

        List<FileResponse> responseList = files.stream()
                .map(this::mapToFileResponse)
                .collect(Collectors.toList());

        sortFiles(responseList, order);
        return paginateList(responseList, page, limit);
    }

    @Transactional(readOnly = true)
    public List<FileResponse> getSharedFiles(String search, String keyword, Integer order, Integer page, Integer limit) {
        String query = StringUtils.hasText(search) ? search : keyword;

        List<PublicUpload> publicUploads;
        if (StringUtils.hasText(query)) {
            publicUploads = publicUploadRepository.searchPublicUploads(query.trim());
        } else {
            publicUploads = publicUploadRepository.findAllByOrderByCreatedAtDesc();
        }

        List<FileResponse> responseList = publicUploads.stream()
                .map(this::mapPublicUploadToFileResponse)
                .collect(Collectors.toList());

        // Fallback: also include legacy shared files from files table if present
        List<FileItem> legacySharedFiles;
        if (StringUtils.hasText(query)) {
            legacySharedFiles = fileItemRepository.searchSharedFiles(StorageType.SHARED_UPLOADS, query.trim());
        } else {
            legacySharedFiles = fileItemRepository.findByStorageTypeOrderByCreatedAtDesc(StorageType.SHARED_UPLOADS);
        }
        for (FileItem legacy : legacySharedFiles) {
            responseList.add(mapToFileResponse(legacy));
        }

        sortFiles(responseList, order);
        return paginateList(responseList, page, limit);
    }

    private void sortFiles(List<FileResponse> list, Integer order) {
        if (order == null) order = 3;
        switch (order) {
            case 1: // Name ASC
                list.sort((a, b) -> a.getOriginalFilename().compareToIgnoreCase(b.getOriginalFilename()));
                break;
            case 2: // Name DESC
                list.sort((a, b) -> b.getOriginalFilename().compareToIgnoreCase(a.getOriginalFilename()));
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
    public FileResponse getPrivateFileDetails(Long fileId, UserPrincipal currentUserPrincipal) {
        FileItem fileItem = fileItemRepository.findByIdAndUserId(fileId, currentUserPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("File not found or access denied"));
        return mapToFileResponse(fileItem);
    }

    @Transactional(readOnly = true)
    public FileResponse getSharedFileDetails(Long fileId) {
        PublicUpload publicUpload = publicUploadRepository.findById(fileId).orElse(null);
        if (publicUpload != null) {
            return mapPublicUploadToFileResponse(publicUpload);
        }

        FileItem fileItem = fileItemRepository.findByIdAndStorageType(fileId, StorageType.SHARED_UPLOADS)
                .orElseThrow(() -> new ResourceNotFoundException("Shared file not found"));
        return mapToFileResponse(fileItem);
    }

    @Transactional
    public FileResponse renameFile(Long fileId, FileUpdateRequest request, UserPrincipal currentUserPrincipal) {
        FileItem fileItem = fileItemRepository.findByIdAndUserId(fileId, currentUserPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("File not found or access denied"));

        String newName = request.getName().trim();
        if (newName.isEmpty()) {
            throw new BadRequestException("Filename cannot be empty");
        }

        fileItem.setOriginalFilename(newName);
        if (request.getStorageType() != null) {
            fileItem.setStorageType(request.getStorageType());
        }
        FileItem updated = fileItemRepository.save(fileItem);
        return mapToFileResponse(updated);
    }

    @Transactional
    public FileResponse moveFile(Long fileId, FileMoveRequest request, UserPrincipal currentUserPrincipal) {
        Long userId = currentUserPrincipal.getId();
        FileItem fileItem = fileItemRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found or access denied"));

        if (fileItem.getStorageType() != StorageType.PRIVATE) {
            throw new BadRequestException("Shared files cannot be moved to private folders. Use copy instead.");
        }

        Folder targetFolder = null;
        if (request.getTargetFolderId() != null) {
            targetFolder = folderRepository.findByIdAndUserId(request.getTargetFolderId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Target folder not found or access denied"));
        }

        fileItem.setFolder(targetFolder);
        FileItem updated = fileItemRepository.save(fileItem);
        return mapToFileResponse(updated);
    }

    @Transactional
    public void deletePrivateFile(Long fileId, UserPrincipal currentUserPrincipal) {
        // Check public_uploads table first
        PublicUpload publicUpload = publicUploadRepository.findById(fileId).orElse(null);
        if (publicUpload != null) {
            cloudinaryService.deleteFile(publicUpload.getCloudinaryPublicId(), publicUpload.getResourceType());
            publicUploadRepository.delete(publicUpload);
            return;
        }

        FileItem fileItem = fileItemRepository.findByIdAndUserId(fileId, currentUserPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("File not found or access denied"));

        cloudinaryService.deleteFile(fileItem.getCloudinaryPublicId(), fileItem.getResourceType());
        fileItemRepository.delete(fileItem);
    }

    @Transactional
    public void deleteSharedFile(Long fileId) {
        PublicUpload publicUpload = publicUploadRepository.findById(fileId).orElse(null);
        if (publicUpload != null) {
            cloudinaryService.deleteFile(publicUpload.getCloudinaryPublicId(), publicUpload.getResourceType());
            publicUploadRepository.delete(publicUpload);
            return;
        }

        FileItem fileItem = fileItemRepository.findByIdAndStorageType(fileId, StorageType.SHARED_UPLOADS).orElse(null);
        if (fileItem != null) {
            cloudinaryService.deleteFile(fileItem.getCloudinaryPublicId(), fileItem.getResourceType());
            fileItemRepository.delete(fileItem);
        }
    }

    public FileResponse mapToFileResponse(FileItem file) {
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

    public FileResponse mapPublicUploadToFileResponse(PublicUpload file) {
        return FileResponse.builder()
                .id(file.getId())
                .originalFilename(file.getOriginalFilename())
                .storedFilename(file.getStoredFilename())
                .mimeType(file.getMimeType())
                .fileSize(file.getFileSize())
                .cloudinaryUrl(file.getCloudinaryUrl())
                .cloudinaryPublicId(file.getCloudinaryPublicId())
                .resourceType(file.getResourceType())
                .storageType(StorageType.SHARED_UPLOADS)
                .folderId(null)
                .ownerId(null)
                .ownerName("Public Uploader")
                .createdAt(file.getCreatedAt())
                .updatedAt(file.getUpdatedAt())
                .build();
    }
}

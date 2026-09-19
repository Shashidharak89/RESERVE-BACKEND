package com.reserve.backend.repository;

import com.reserve.backend.entity.FileItem;
import com.reserve.backend.entity.StorageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileItemRepository extends JpaRepository<FileItem, Long> {

    List<FileItem> findByUserIdAndStorageTypeAndFolderIsNullOrderByOriginalFilenameAsc(Long userId, StorageType storageType);

    List<FileItem> findByUserIdAndStorageTypeAndFolderIdOrderByOriginalFilenameAsc(Long userId, StorageType storageType, Long folderId);

    List<FileItem> findByUserIdAndStorageTypeOrderByOriginalFilenameAsc(Long userId, StorageType storageType);

    List<FileItem> findByStorageTypeOrderByCreatedAtDesc(StorageType storageType);

    Optional<FileItem> findByIdAndUserId(Long id, Long userId);

    Optional<FileItem> findByIdAndStorageType(Long id, StorageType storageType);

    @Query("SELECT f FROM FileItem f WHERE f.user.id = :userId AND f.storageType = :storageType AND LOWER(f.originalFilename) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY f.originalFilename ASC")
    List<FileItem> searchPrivateFiles(@Param("userId") Long userId, @Param("storageType") StorageType storageType, @Param("query") String query);

    @Query("SELECT f FROM FileItem f WHERE f.storageType = :storageType AND LOWER(f.originalFilename) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY f.createdAt DESC")
    List<FileItem> searchSharedFiles(@Param("storageType") StorageType storageType, @Param("query") String query);
}

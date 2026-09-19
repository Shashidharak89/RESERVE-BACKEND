package com.reserve.backend.repository;

import com.reserve.backend.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FolderRepository extends JpaRepository<Folder, Long> {

    List<Folder> findByUserIdAndParentFolderIsNullOrderByNameAsc(Long userId);

    List<Folder> findByUserIdAndParentFolderIdOrderByNameAsc(Long userId, Long parentId);

    List<Folder> findByUserIdOrderByNameAsc(Long userId);

    Optional<Folder> findByIdAndUserId(Long id, Long userId);

    Optional<Folder> findByIdAndVisibility(Long id, com.reserve.backend.entity.Visibility visibility);

    boolean existsByNameAndUserIdAndParentFolder(String name, Long userId, Folder parentFolder);

    boolean existsByNameAndUserIdAndParentFolderIsNull(String name, Long userId);
}

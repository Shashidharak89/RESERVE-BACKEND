package com.reserve.backend.repository;

import com.reserve.backend.entity.PublicUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PublicUploadRepository extends JpaRepository<PublicUpload, Long> {
    List<PublicUpload> findAllByOrderByCreatedAtDesc();

    @Query("SELECT p FROM PublicUpload p WHERE LOWER(p.originalFilename) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY p.createdAt DESC")
    List<PublicUpload> searchPublicUploads(@Param("query") String query);
}

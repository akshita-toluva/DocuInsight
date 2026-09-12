package com.docuinsight.docuinsight.repository;

import com.docuinsight.docuinsight.model.UploadedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UploadedFileRepository extends JpaRepository<UploadedFile,Long> {
    List<UploadedFile> findByUserId(Long userId);

}

package com.docuinsight.docuinsight.repository;

import com.docuinsight.docuinsight.model.MultiDocumentReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MultiDocumentReportRepository extends JpaRepository<MultiDocumentReport,Long> {

    List<MultiDocumentReport> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<MultiDocumentReport> findByIdAndUserId(Long id, Long userId);

}

package com.docuinsight.docuinsight.repository;

import com.docuinsight.docuinsight.model.Report;
import com.docuinsight.docuinsight.model.Report.ReportStatus;
import com.docuinsight.docuinsight.model.Report.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report,Long> {

    List<Report> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Report> findByUploadedFileIdOrderByCreatedAtDesc(Long fileId);

    Optional<Report> findByIdAndUserId(Long reportId, Long userId);
    //the file may return even null , if handled in such manner it wont through null pointer exception

    Optional<Report> findByUploadedFileIdAndReportTypeAndStatus(
            Long fieldId,
            ReportType reportType,
            ReportStatus reportStatus
    );

    long countByUserId(Long userId);

    List<Report> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId,ReportStatus status);

    List<Report> findByUploadedFileIdAndReportTypeAndStatusOrderByCreatedAtDesc(
            Long fileId, ReportType reportType, ReportStatus status
    );
}

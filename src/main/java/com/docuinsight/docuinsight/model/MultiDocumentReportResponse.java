package com.docuinsight.docuinsight.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class MultiDocumentReportResponse {
    private Long id;
    private List<String> fileNames;
    private List<Long> fileIds;
    private String reportType;
    private String status;
    private String reportContent;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public static MultiDocumentReportResponse from(MultiDocumentReport report) {
        MultiDocumentReportResponse res = new MultiDocumentReportResponse();
        res.id = report.getId();
        res.fileNames = report.getFiles().stream()
                .map(UploadedFile::getFileName)
                .collect(Collectors.toList());
        res.fileIds = report.getFiles().stream()
                .map(UploadedFile::getId)
                .collect(Collectors.toList());
        res.reportType = report.getReportType().name();
        res.status = report.getStatus().name();
        res.reportContent = report.getReportContent();
        res.createdAt = report.getCreatedAt();
        res.completedAt = report.getCompletedAt();
        return res;
    }

    public Long getId() {
        return id;
    }

    public List<String> getFileNames() {
        return fileNames;
    }

    public List<Long> getFileIds() {
        return fileIds;
    }

    public String getReportType() {
        return reportType;
    }

    public String getStatus() {
        return status;
    }

    public String getReportContent() {
        return reportContent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;

    }
}

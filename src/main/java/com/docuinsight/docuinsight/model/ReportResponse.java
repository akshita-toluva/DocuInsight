package com.docuinsight.docuinsight.model;

import java.time.LocalDateTime;

public class ReportResponse {
    private Long reportId;
    private Long fileId;
    private String fileName;
    private String reportType;
    private String status;
    private String reportContent;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private Integer tokenCount;
    private boolean cached;

    public static ReportResponse from(Report report,boolean cached){
        ReportResponse r=new ReportResponse();
        r.reportId= report.getId();
        r.fileId=report.getUploadedFile().getId();
        r.fileName=report.getUploadedFile().getFileName();
        r.reportType=report.getReportType().name();
        r.status=report.getStatus().name();
        r.reportContent=report.getReportContent();
        r.errorMessage = report.getErrorMessage();
        r.createdAt    = report.getCreatedAt();
        r.completedAt  = report.getCompletedAt();
        r.tokenCount   = report.getTokenCount();
        r.cached       = cached;
        return r;
    }

    public static ReportResponse from(Report report){
         return from(report,false);
    }

    public Long getReportId() {
        return reportId;
    }

    public Long getFileId() {
        return fileId;
    }

    public String getFileName() {
        return fileName;
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

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public boolean isCached() {
        return cached;
    }
}

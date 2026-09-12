package com.docuinsight.docuinsight.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class MultiDocumentReportRequest {

    @NotEmpty(message = "fileIds list cannot be empty")
    @Size(min = 2, message = "At least 2 file IDs required for multi-document analysis")
    private List<Long> fileIds;

    private MultiDocumentReport.ReportType reportType= MultiDocumentReport.ReportType.EXECUTIVE;

    private String customPrompt;


    public List<Long> getFileIds() {
        return fileIds;
    }

    public void setFileIds(List<Long> fileIds) {
        this.fileIds = fileIds;
    }

    public MultiDocumentReport.ReportType getReportType() {
        return reportType;
    }

    public void setReportType(MultiDocumentReport.ReportType reportType) {
        this.reportType = reportType;
    }

    public String getCustomPrompt() {
        return customPrompt;
    }

    public void setCustomPrompt(String customPrompt) {
        this.customPrompt = customPrompt;
    }
}
